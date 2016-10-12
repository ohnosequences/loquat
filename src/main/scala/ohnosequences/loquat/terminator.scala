package ohnosequences.loquat

import utils._

import ohnosequences.statika._

import com.typesafe.scalalogging.LazyLogging
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

import ohnosequences.awstools._, sqs._

import com.amazonaws.{ services => amzn }

import scala.collection.JavaConversions._
import scala.util.Try

import better.files._


private[loquat]
case class TerminationDaemonBundle(
  val config: AnyLoquatConfig,
  val scheduler: Scheduler,
  val initialCount: Int
) extends Bundle() with LazyLogging {

  lazy val aws = instanceAWSClients(config)

  private val successResults = scala.collection.mutable.HashMap[String, String]()
  private val failedResults = scala.collection.mutable.HashMap[String, String]()

  lazy val managerCreationTime: Option[FiniteDuration] =
    aws.as.getCreatedTime(config.resourceNames.managerGroup)
      .map{ _.getTime.millis }

  def instructions: AnyInstructions = LazyTry[Unit] {
    scheduler.repeat(
      after = 1.minute,
      every = 1.minute
    )(checkConditions)
  } -&- say("Termination daemon started")


  def checkConditions(): Unit = {
    logger.info(s"Checking termination conditions")

    aws.sqs.get(config.resourceNames.outputQueue) match {
      case scala.util.Failure(ex) => {
        logger.error(s"Couldn't access output queue: ${config.resourceNames.outputQueue}")
        // FIXME: check typical exceptions
        logger.error(ex.toString)
      }
      case scala.util.Success(outputQueue) =>
        receiveProcessingResults(outputQueue) match {
          case scala.util.Failure(t) => {
            logger.error(s"Couldn't poll the queue: ${config.resourceNames.outputQueue}\n${t.getMessage()}")
          }
          case scala.util.Success(polledMessages) => {
            polledMessages.foreach { result =>
              successResults.put(result.id, result.message)
            }
          }
        }
    }
    logger.debug(s"Success results: ${successResults.size}")

    aws.sqs.get(config.resourceNames.errorQueue) match {
      case scala.util.Failure(ex) => {
        logger.error(s"Couldn't access error queue: ${config.resourceNames.errorQueue}")
        // FIXME: check typical exceptions
        logger.error(ex.toString)
      }
      case scala.util.Success(errorQueue) =>
        receiveProcessingResults(errorQueue) match {
          case scala.util.Failure(t) => {
            logger.error(s"Couldn't poll the queue: ${config.resourceNames.errorQueue}\n${t.getMessage()}")
          }
          case scala.util.Success(polledMessages) => {
            polledMessages.foreach { result =>
              failedResults.put(result.id, result.message)
            }
          }
        }
    }
    logger.debug(s"Failure results: ${failedResults.size}")

    lazy val afterInitial = TerminateAfterInitialDataMappings(
      config.terminationConfig.terminateAfterInitialDataMappings,
      initialCount,
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

  // TODO: use approxMsgAvailable/InFlight instead of polling (it's too slow and expensive)
  def receiveProcessingResults(queue: sqs.Queue): Try[Seq[ProcessingResult]] = {

    queue.poll(
      timeout = 20.seconds
    ).map { msgs =>
      msgs.map { msg =>
        upickle.default.read[ProcessingResult](msg.body)
      }
    }
  }

}
