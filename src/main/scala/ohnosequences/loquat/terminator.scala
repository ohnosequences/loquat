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
) extends LazyLogging {

  lazy val aws: AWSClients = AWSClients.create(new InstanceProfileCredentialsProvider())

  private val successResults = scala.collection.mutable.HashMap[String, String]()
  private val failedResults = scala.collection.mutable.HashMap[String, String]()

  def checkConditions(): Unit = {
    logger.info("TerminationDeaemon conditions: " + config.terminationConfig)
    logger.info("TerminationDeaemon success results: " + successResults.size)
    logger.info("TerminationDeaemon failure results: " + failedResults.size)

    // FIXME: we don't need parsing here, only the numbers of messages
    receiveDataMappingsResults(config.resourceNames.outputQueue).foreach { case (handle, result) =>
      successResults.put(result.id, result.message)
    }

    receiveDataMappingsResults(config.resourceNames.errorQueue).foreach { case (handle, result) =>
      failedResults.put(result.id, result.message)
    }

    val reason: Option[AnyTerminationReason] = terminationReason(
      terminationConfig = config.terminationConfig,
      successResultsCount = successResults.size,
      failedResultsCount = failedResults.size,
      initialDataMappingsCount = config.dataMappings.length
    )

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
    initialDataMappingsCount: Int
  ): Option[AnyTerminationReason] = {

    lazy val afterInitial = TerminateAfterInitialDataMappings(
      terminationConfig.terminateAfterInitialDataMappings,
      initialDataMappingsCount,
      successResultsCount
    )
    lazy val tooManyErrors = TerminateWithTooManyErrors(
      terminationConfig.errorsThreshold,
      failedResultsCount
    )
    lazy val globalTimeout = TerminateAfterGlobalTimeout(
      terminationConfig.globalTimeout,
      aws.as.getCreatedTime(config.resourceNames.managerGroup).map{ _.getTime.seconds }
    )

         if (afterInitial.check) Some(afterInitial)
    else if (tooManyErrors.check) Some(tooManyErrors)
    else if (globalTimeout.check) Some(globalTimeout)
    else None
  }

}
