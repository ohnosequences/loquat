package ohnosequences.loquat

import utils._, files._
import ohnosequences.statika._
import ohnosequences.datasets._
import ohnosequences.awstools._, sqs._, s3._, ec2._, regions._
import com.amazonaws.services.s3.transfer.TransferManager
import com.typesafe.scalalogging.LazyLogging
import scala.concurrent._, duration._
import scala.util.Try

trait AnyWorkerBundle extends AnyBundle {

  type DataProcessingBundle <: AnyDataProcessingBundle
  val  instructionsBundle: DataProcessingBundle

  type Config <: AnyLoquatConfig
  val  config: Config

  val scheduler = Scheduler(2)

  lazy val loggerBundle = LogUploaderBundle(config, scheduler)

  val bundleDependencies: List[AnyBundle] = List(
    loggerBundle,
    instructionsBundle
  )

  def instructions: AnyInstructions = LazyTry {
    new DataProcessor(config, loggerBundle, instructionsBundle).runLoop
  }
}

abstract class WorkerBundle[
  I <: AnyDataProcessingBundle,
  C <: AnyLoquatConfig
](val instructionsBundle: I,
  val config: C
) extends AnyWorkerBundle {

  type DataProcessingBundle = I
  type Config = C
}


// TODO: rewrite all this and make it Worker's install
class DataProcessor(
  val config: AnyLoquatConfig,
  val loggerBundle: LogUploaderBundle,
  val instructionsBundle: AnyDataProcessingBundle
) extends LazyLogging {

  final val workingDir = file("/media/ephemeral0/applicator/loquat")

  lazy val aws = AWSClients(config.region)

  // FIXME: don't use Try.get
  import ohnosequences.awstools.sqs._
  val inputQueue = aws.sqs.getQueue(config.resourceNames.inputQueue).get
  val errorQueue = aws.sqs.getQueue(config.resourceNames.errorQueue).get
  val outputQueue = aws.sqs.getQueue(config.resourceNames.outputQueue).get

  val instance = aws.ec2.getCurrentInstance.get

  @volatile var stopped = false

  def waitForResult[R <: AnyResult](futureResult: Future[R], message: Message): Result[FiniteDuration] = {
    val startTime = System.currentTimeMillis.millis
    val step = 5.seconds

    def timeSpent: FiniteDuration = {
      val currentTime = System.currentTimeMillis.millis
      (currentTime - startTime).toSeconds.seconds
    }

    val taskProcessingTimeout: FiniteDuration =
      config.terminationConfig.taskProcessingTimeout.getOrElse(12.hours)

    @scala.annotation.tailrec
    def waitMore(tries: Int): AnyResult = {
      if(timeSpent > taskProcessingTimeout) {
        val msg = s"Timeout exceeded: ${timeSpent} spent"
        terminateWorker(msg)
        Failure(msg)
      } else {
        futureResult.value match {
          case None => {
            // every 5min we extend it for 6min
            if (tries % (5*60) == 0) {
              if (Try(message.changeVisibility(6.minutes)).isFailure)
                logger.warn("Couldn't change the visibility globalTimeout")
              // FIXME: something weird is happening here
            }
            Thread.sleep(step.toMillis)
            // logger.info("Solving dataMapping: " + timeSpent.prettyPrint)
            waitMore(tries + 1)
          }
          case Some(scala.util.Success(r)) => {
            logger.info("Got a result: " + r.trace.toString)
            r
          }
          case Some(scala.util.Failure(t)) => Failure(s"future error: ${t.getMessage}")
        }
      }
    }

    waitMore(tries = 0) match {
      case Failure(tr) => Failure(tr)
      case Success(tr, _) => Success(tr, timeSpent)
    }
  }

  def terminateWorker(msg: String): Unit = {
    stopped = true

    logger.error(msg)
    logger.error("Terminating instance")

    val msgWithID = s"Worker instance ${instance.id}: ${msg}"
    errorQueue.sendOne(msgWithID).recover { case e =>
      logger.error(s"Couldn't send failure SQS message: ${e}")
    }

    loggerBundle.uploadLog()
    loggerBundle.failureNotification(s"Worker instance ${instance.id} terminated with a fatal error").recover { case e =>
      logger.error(s"Couldn't send failure SNS notification: ${e}")
    }

    Thread.sleep(15.minutes.toMillis)

    instance.terminate.getOrElse {
      logger.error(s"Couldn't teminate the instance")
    }
  }

  private def processDataMapping(
    transferManager: TransferManager,
    dataMapping: SimpleDataMapping,
    workingDir: File
  ): AnyResult = {

    try {
      logger.debug("Preparing dataMapping input")

      val inputDir = (workingDir / "input").createDirectory

      val inputFiles: Map[String, File] = dataMapping.inputs.map { case (name, resource) =>

        logger.debug(s"Trying to create input object [${name}] from \n${resource}")

        resource match {
          case MessageResource(msg) => {
            val destination: File = inputDir / name
            destination.createFile.overwrite(msg)
            (name -> destination)
          }
          case S3Resource(s3Address) => {
            // FIXME: this shouldn't ignore the returned Try
            val destination: File = transferManager.download(
              s3Address,
              inputDir / name
            ).get
            (name -> destination)
          }
        }
      }

      // logger.debug("Processing data in: " + workingDir.path)
      val result = instructionsBundle.runProcess(workingDir, inputFiles)

      val resultDescription = ProcessingResult(dataMapping.id, result.toString)

      result match {
        case Failure(tr) => {
          logger.error(s"Script finished with non zero code: ${result}. publishing it to the error queue.")
          errorQueue.sendOne(s"Worker instance ${instance.id}: ${resultDescription}")
          result
        }
        case Success(tr, outputFileMap) => {
          // FIXME: do it more careful
          val outputMap: Map[File, AnyS3Address] =
            outputFileMap.map { case (name, file) =>
              file -> dataMapping.outputs(name).resource
            }
          // TODO: simplify this huge if-else statement
          if (outputMap.keys.forall(_.exists)) {

            val uploadTries = outputMap flatMap { case (file, s3Address) =>
              if (config.skipEmptyResults && file.isEmpty) {
                logger.info(s"Output file [${file.getName}] is empty. Skipping it.")
                None
              } else {
                logger.info(s"Publishing output object: [${file.getName}]")
                Some(
                  transferManager.upload(
                    file,
                    s3Address,
                    Map(
                      "artifact-org"     -> config.metadata.organization,
                      "artifact-name"    -> config.metadata.artifact,
                      "artifact-version" -> config.metadata.version,
                      "artifact-url"     -> config.metadata.artifactUrl
                    )
                  )
                )
              }
            }

            // TODO: check whether we can fold Try's here somehow
            if (uploadTries.forall(_.isSuccess)) {
              logger.info("Finished uploading output files. publishing message to the output queue.")
              outputQueue.sendOne(resultDescription.toString)
              result //-&- success(s"task [${dataMapping.id}] is successfully finished", ())
            } else {
              logger.error(s"Some uploads failed: ${uploadTries.filter(_.isFailure)}")
              tr +: Failure(Seq("failed to upload output files"))
            }

          } else {

            val missingFiles = outputMap.keys.filterNot(_.exists).map(_.path)
            logger.error(s"Some output files don't exist: ${missingFiles}")
            tr +: Failure(Seq(s"Couldn't upload results, because some output files don't exist: ${missingFiles}"))

          }
        }
      }
    } catch {
      case t: Throwable => {
        terminateWorker(s"Fatal failure during dataMapping processing: ${t}")
        Failure(Seq(t.toString))
      }
    }
  }

  def waitForTask(): Message = inputQueue.poll(
    timeout = Duration.Inf,
    amountLimit = Some(1),
    adjustRequest = { _.withWaitTimeSeconds(10) }
  ).toOption.flatMap { _.headOption }.getOrElse {
    logger.info("Didn't get any input tasks. Probably the queue is empty. Retrying...")
    Thread.sleep(1.minute.toMillis)
    waitForTask()
  }

  def runLoop(): Unit = {

    logger.info("DataProcessor started at " + instance.id)

    if (workingDir.exists) {
      logger.debug("Deleting working directory: " + workingDir.path)
      workingDir.deleteRecursively()
    }

    logger.info("Creating working directory: " + workingDir.path)
    workingDir.createDirectory

    while(!stopped) {
      try {
        val transferManager = aws.s3.createTransferManager

        logger.info("Waiting for new data")

        val message = waitForTask()

        // instance.foreach(_.createTag(StatusTag.processing))
        val dataMapping = SimpleDataMapping.deserialize(message.body)

        logger.info(s"Processing message [${dataMapping.id}]")

        import scala.concurrent.ExecutionContext.Implicits._
        val futureResult = Future {
          processDataMapping(transferManager, dataMapping, workingDir)
        }

        // NOTE: this is blocking until the Future gets a result
        val dataMappingResult = waitForResult(futureResult, message)

        // FIXME: check this. what happens if result has failures?
        if (dataMappingResult.isSuccessful) {
          logger.info("Result was successful. deleting message from the input queue")
          message.delete()
        }

        transferManager.shutdown()
      } catch {
        case t: Throwable => terminateWorker(s"Fatal failure during dataMapping processing: ${t}")
      }
    }
  }

}
