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
import ohnosequences.awstools.sqs
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

  lazy val managerCreationTime: Option[FiniteDuration] =
    aws.as.getCreatedTime(config.resourceNames.managerGroup)
      .map{ _.getTime.millis }

  def instructions: AnyInstructions = LazyTry[Unit] {
    scheduler.repeat(
      after = 3.minutes,
      every = 3.minutes
    )(checkConditions)
  } -&- say("Termination daemon started")


  def checkConditions(): Unit = {
    logger.info(s"Checking termination conditions")

    aws.sqs.getQueueByName(config.resourceNames.outputQueue) match {
      case None => logger.error(s"Couldn't access output queue: ${config.resourceNames.outputQueue}")
      case Some(outputQueue) =>
        receiveDataMappingsResults(outputQueue).foreach { case result =>
          successResults.put(result.id, result.message)
        }
    }
    logger.debug("Success results: " + successResults.size)

    aws.sqs.getQueueByName(config.resourceNames.errorQueue) match {
      case None => logger.error(s"Couldn't access error queue: ${config.resourceNames.errorQueue}")
      case Some(errorQueue) =>
        receiveDataMappingsResults(errorQueue).foreach { case result =>
          failedResults.put(result.id, result.message)
        }
    }
    logger.debug("Failure results: " + failedResults.size)

    lazy val afterInitial = TerminateAfterInitialDataMappings(
      config.terminationConfig.terminateAfterInitialDataMappings,
      config.dataMappings.length,
      successResults.size
    )
    logger.debug(s"${afterInitial}: ${afterInitial.check}")

    lazy val tooManyErrors = TerminateWithTooManyErrors(
      config.terminationConfig.errorsThreshold,
      failedResults.size
    )
    logger.debug(s"${tooManyErrors}: ${tooManyErrors.check}")

    lazy val globalTimeout = TerminateAfterGlobalTimeout(
      config.terminationConfig.globalTimeout,
      managerCreationTime
    )
    logger.debug(s"${globalTimeout}: ${globalTimeout.check}")

    val reason: Option[AnyTerminationReason] =
           if (afterInitial.check) Some(afterInitial)
      else if (tooManyErrors.check) Some(tooManyErrors)
      else if (globalTimeout.check) Some(globalTimeout)
      else None

    logger.info(s"Termination reason: ${reason}")

    // if there is a reason, we undeploy everything
    reason.foreach{ LoquatOps.undeploy(config, aws, _) }
  }

  def receiveDataMappingsResults(queue: sqs.Queue): List[ProcessingResult] = {
    getQueueMessages(queue).map { message =>
      upickle.default.read[ProcessingResult](message.body)
    }
  }

  def getQueueMessages(queue: sqs.Queue): List[sqs.Message] = {
    var messages = ListBuffer[sqs.Message]()
    var empty = false
    var i = 0
    while(!empty && i < 10) {
      val chuck = queue.receiveMessages(10)
      if (chuck.isEmpty) { empty = true }
      messages ++= chuck
      i += 1
    }
    messages.toList
  }

}
