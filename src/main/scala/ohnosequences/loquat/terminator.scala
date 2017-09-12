package ohnosequences.loquat

import utils._
import ohnosequences.statika._
import ohnosequences.awstools._, sqs._, autoscaling._, regions._
import com.typesafe.scalalogging.LazyLogging
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import com.amazonaws.{ services => amzn }
import scala.collection.JavaConversions._
import scala.util.Try


private[loquat]
case class TerminationDaemonBundle(
  val config: AnyLoquatConfig,
  val scheduler: Scheduler,
  val initialCount: Int
) extends Bundle() with LazyLogging {

  lazy val aws = AWSClients(config.region)

  lazy val managerCreationTime: Option[FiniteDuration] =
    aws.as.getGroup(config.resourceNames.managerGroup)
      .map { _.getCreatedTime.getTime.millis }
      .toOption


  // NOTE: if these requests fail, there's no point to continue, so I just use get
  lazy val inputQueue:  Queue = aws.sqs.getQueue(config.resourceNames.inputQueue).get
  lazy val outputQueue: Queue = aws.sqs.getQueue(config.resourceNames.outputQueue).get
  lazy val errorQueue:  Queue = aws.sqs.getQueue(config.resourceNames.errorQueue).get

  def instructions: AnyInstructions =
    LazyTry[Unit] {
      checkAndTerminate(
        after = 1.minute,
        every = 3.minutes
      )
    } -&- say("Termination daemon started")


  def checkAndTerminate(
    after: FiniteDuration,
    every: FiniteDuration
  ) = {
    scheduler.repeat(after, every){
      checkConditions(recheck = false)
    }
  }


  def checkConditions(recheck: Boolean): Option[AnyTerminationReason] = {

    val numbers: AllQueuesNumbers = averageQueuesNumbers(
      inputQueue,
      outputQueue,
      errorQueue
    )(tries = if (recheck) 5 else 1)

    logger.info(s"Queues state:\n${numbers.toString}")

    logger.info(s"Checking termination conditions")
    lazy val afterInitial = TerminateAfterInitialDataMappings(
      config.terminationConfig.terminateAfterInitialDataMappings,
      initialCount,
      numbers.inputQ,
      numbers.outputQ
    )
    logger.info(s"Terminate after initial tasks:  ${afterInitial.check}")

    lazy val tooManyErrors = TerminateWithTooManyErrors(
      config.terminationConfig.errorsThreshold,
      numbers.errorQ.available
    )
    logger.info(s"Terminate with too many errors: ${tooManyErrors.check}")

    lazy val globalTimeout = TerminateAfterGlobalTimeout(
      config.terminationConfig.globalTimeout,
      managerCreationTime
    )
    logger.info(s"Terminate after global timeout: ${globalTimeout.check}")

    val reason: Option[AnyTerminationReason] =
           if (afterInitial.check) Some(afterInitial)
      else if (tooManyErrors.check) Some(tooManyErrors)
      else if (globalTimeout.check) Some(globalTimeout)
      else None


    reason match {
      case Some(sureReason) if (recheck) => {
        logger.info(s"Termination reason: ${sureReason}")
        LoquatOps.undeploy(config, aws, sureReason)
        reason
      }
      /* if there is a reason, we first run a more robust check (with accumulating queue numbers several times) */
      case Some(unsureReason) => checkConditions(recheck = true)
      case None => None
    }
  }

  /* This method checks each queue's approximate available/in-flight messages numbers several times (with pauses) and returns their average. This way you can be more or less sure that the numbers you get are consistent. */
  def averageQueuesNumbers(
    inputQ: Queue,
    outputQ: Queue,
    errorQ: Queue
  )(tries: Int): AllQueuesNumbers = {

    def getNumbers = AllQueuesNumbers(
      QueueNumbers(inputQ.approxMsgAvailable,  inputQ.approxMsgInFlight),
      QueueNumbers(outputQ.approxMsgAvailable, outputQ.approxMsgInFlight),
      QueueNumbers(errorQ.approxMsgAvailable,  errorQ.approxMsgInFlight)
    )

    def averageOf(vals: Seq[Int]): Int = vals.sum / vals.length

    @scala.annotation.tailrec
    def getAverage(triesLeft: Int, acc: Seq[AllQueuesNumbers]): AllQueuesNumbers = {

      if (triesLeft > 0) {

        sleep(5.seconds)
        getAverage(triesLeft - 1, getNumbers +: acc)
      } else {

        AllQueuesNumbers(
          QueueNumbers(
            averageOf( acc.map { _.inputQ.available } ),
            averageOf( acc.map { _.inputQ.inFlight } )
          ),
          QueueNumbers(
            averageOf( acc.map { _.outputQ.available } ),
            averageOf( acc.map { _.outputQ.inFlight } )
          ),
          QueueNumbers(
            averageOf( acc.map { _.errorQ.available } ),
            averageOf( acc.map { _.errorQ.inFlight } )
          )
        )
      }
    }

    getAverage(tries, Seq())
  }
}

case class QueueNumbers(
  val available: Int,
  val inFlight: Int
) {

  override def toString: String = s"${available} available, ${inFlight} in flight"
}

case class AllQueuesNumbers(
  val inputQ: QueueNumbers,
  val outputQ: QueueNumbers,
  val errorQ: QueueNumbers
) {

  override def toString: String = Seq(
    s"input  queue: ${inputQ}",
    s"output queue: ${outputQ}",
    s"error  queue: ${errorQ}"
  ).mkString("\n")
}
