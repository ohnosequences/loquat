package ohnosequences.loquat

import utils._

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._
import ohnosequences.statika.results._

import com.typesafe.scalalogging.LazyLogging
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

import ohnosequences.awstools.AWSClients
import ohnosequences.awstools.s3._
import com.amazonaws.auth.InstanceProfileCredentialsProvider
import java.util.concurrent._
import scala.util.Try
import better.files._

private[loquat]
case class TerminationDaemonBundle(
  val config: AnyLoquatConfig,
  val scheduler: Scheduler
) extends Bundle() with LazyLogging {

  lazy val aws: AWSClients = AWSClients.create(new InstanceProfileCredentialsProvider())

  private val successResults = scala.collection.mutable.HashMap[String, String]()
  private val failedResults = scala.collection.mutable.HashMap[String, String]()

  lazy val mangerCreationTime: Option[FiniteDuration] =
    aws.as.getCreatedTime(config.resourceNames.managerGroup)
      .map{ _.getTime.millis }

  def instructions: AnyInstructions = LazyTry[Unit] {
    scheduler.repeat(
      after = 3.minutes,
      every = 3.minutes
    )(checkConditions)
  } -&- say("Termination daemon started")


  def checkConditions(): Unit = {
    logger.info(s"Checking termination conditions:")
    // FIXME: we don't need parsing here, only the numbers of messages
    receiveDataMappingsResults(config.resourceNames.outputQueue).foreach { case (handle, result) =>
      successResults.put(result.id, result.message)
    }
    // logger.debug("success results: " + successResults.size)

    receiveDataMappingsResults(config.resourceNames.errorQueue).foreach { case (handle, result) =>
      failedResults.put(result.id, result.message)
    }
    // logger.debug("failure results: " + failedResults.size)

    val reason: Option[AnyTerminationReason] = terminationReason(
      terminationConfig = config.terminationConfig,
      successResultsCount = successResults.size,
      failedResultsCount = failedResults.size,
      initialDataMappingsCount = config.dataMappings.length,
      creationTime = mangerCreationTime
    )
    logger.info(s"Termination reason: ${reason}")

    // if there is a reason inside, we undeploy everything
    reason.foreach{ LoquatOps.undeploy(config, aws, _) }
  }

  def receiveDataMappingsResults(queueName: String): List[(String, ProcessingResult)] = {
    val rawMessages: List[(String, String)] = getQueueMessagesWithHandles(queueName)

    rawMessages.map { case (handle, rawMessageBody) =>
      // TODO: check this:
      val resultDescription: ProcessingResult = upickle.default.read[ProcessingResult](rawMessageBody)
        // upickle.default.read[ProcessingResult](snsMessage.Message.replace("\\\"", "\""))
      logger.info(resultDescription.toString)
      (handle, resultDescription)
    }
  }

  def getQueueMessagesWithHandles(queueName: String): List[(String, String)] = {
    aws.sqs.getQueueByName(queueName) match {
      case None => Nil
      case Some(queue) => {
        var messages = ListBuffer[(String, String)]()
        var empty = false
        var i = 0
        while(!empty && i < 10) {
          val chuck = queue.receiveMessages(10)
          if (chuck.isEmpty) {
            empty = true
          }
          messages ++= chuck.map { m=>
            m.receiptHandle -> m.body
          }
          i += 1
        }
        messages.toList
      }
    }
  }

  def terminationReason(
    terminationConfig: TerminationConfig,
    successResultsCount: Int,
    failedResultsCount: Int,
    initialDataMappingsCount: Int,
    creationTime: Option[FiniteDuration]
  ): Option[AnyTerminationReason] = {

    lazy val afterInitial = TerminateAfterInitialDataMappings(
      terminationConfig.terminateAfterInitialDataMappings,
      initialDataMappingsCount,
      successResultsCount
    )
    logger.debug(s"${afterInitial}: ${afterInitial.check}")

    lazy val tooManyErrors = TerminateWithTooManyErrors(
      terminationConfig.errorsThreshold,
      failedResultsCount
    )
    logger.debug(s"${tooManyErrors}: ${tooManyErrors.check}")

    lazy val globalTimeout = TerminateAfterGlobalTimeout(
      terminationConfig.globalTimeout,
      creationTime
    )
    logger.debug(s"${globalTimeout}: ${globalTimeout.check}")

         if (afterInitial.check) Some(afterInitial)
    else if (tooManyErrors.check) Some(tooManyErrors)
    else if (globalTimeout.check) Some(globalTimeout)
    else None
  }

}
