package ohnosequences.loquat

import utils._

import ohnosequences.statika._

import com.typesafe.scalalogging.LazyLogging
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

import ohnosequences.awstools.s3._
import ohnosequences.awstools.sqs

import com.amazonaws.{ services => amzn }

import java.util.concurrent._

import scala.collection.JavaConversions._
import scala.util.Try

import better.files._


private[loquat]
case class TerminationDaemonBundle(
  val config: AnyLoquatConfig,
  val scheduler: Scheduler
) extends Bundle() with LazyLogging {

  lazy val aws = instanceAWSClients(config)

  private val successResults = scala.collection.mutable.HashMap[String, String]()
  private val failedResults = scala.collection.mutable.HashMap[String, String]()

  lazy val managerCreationTime: Option[FiniteDuration] =
    aws.as.getCreatedTime(config.resourceNames.managerGroup)
      .map{ _.getTime.millis }

  def instructions: AnyInstructions = LazyTry[Unit] {
    scheduler.repeat(
      after = 3.minutes,
      every = 30.seconds //3.minutes
    )(checkConditions)
  } -&- say("Termination daemon started")


  def checkConditions(): Unit = {
    logger.info(s"Checking termination conditions")

    aws.sqs.getQueueByName(config.resourceNames.outputQueue) match {
      case None => logger.error(s"Couldn't access output queue: ${config.resourceNames.outputQueue}")
      case Some(outputQueue) =>
        receiveProcessingResults(outputQueue).foreach { result =>
          successResults.put(result.id, result.message)
        }
    }
    logger.debug(s"Success results: ${successResults.size}")

    aws.sqs.getQueueByName(config.resourceNames.errorQueue) match {
      case None => logger.error(s"Couldn't access error queue: ${config.resourceNames.errorQueue}")
      case Some(errorQueue) =>
        receiveProcessingResults(errorQueue).foreach { result =>
          failedResults.put(result.id, result.message)
        }
    }
    logger.debug(s"Failure results: ${failedResults.size}")

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

  def receiveProcessingResults(queue: sqs.Queue): List[ProcessingResult] = {

    /* Note that this request does so called short-polling, see the [Amazon documentation](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/sqs/model/ReceiveMessageRequest.html).
       Long polling doesn't work here, because it returns too many responses with the same messages (so it's not clear when you can stop polling).
    */
    def getMessageBodies(): List[String] = {
      val bodies = queue.sqs.receiveMessage(
        new amzn.sqs.model.ReceiveMessageRequest(queue.url)
          .withMaxNumberOfMessages(10) // this is the maximum we can ask Amazon for
      ).getMessages.toList.map{ _.getBody }
      // logger.debug(s"Received [${bodies.length}] messages from the queue ${queue.name}")
      bodies
    }

    /* We are polling the queue until we get an empty response 5+ times in a row,
       because short polling eventually returns false empty responses.
    */
    def pollQueue: List[String] = {

      @scala.annotation.tailrec
        def pollQueue_rec(
          acc: scala.collection.mutable.ListBuffer[String],
          tries: Int
        ): List[String] = {
          val response = getMessageBodies()

          if (response.isEmpty) {
            if (tries > 5) acc.toList
            else pollQueue_rec(acc ++= response, tries + 1)
          }
          else pollQueue_rec(acc ++= response, 0)
        }

      pollQueue_rec(scala.collection.mutable.ListBuffer(), 0)
    }

    pollQueue.map { upickle.default.read[ProcessingResult](_) }

  }

}
