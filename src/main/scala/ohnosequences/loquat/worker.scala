package ohnosequences.loquat

import utils._

import ohnosequences.statika._

import ohnosequences.datasets._

import ohnosequences.awstools.sqs.Message
import ohnosequences.awstools.sqs.Queue
import ohnosequences.awstools.s3._

import com.typesafe.scalalogging.LazyLogging

import better.files._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

import upickle.Js

import com.amazonaws.services.s3.transfer._
import com.amazonaws.services.s3.model._


trait AnyWorkerBundle extends AnyBundle {

  type DataProcessingBundle <: AnyDataProcessingBundle
  val  instructionsBundle: DataProcessingBundle

  type Config <: AnyLoquatConfig
  val  config: Config

  val scheduler = Scheduler(2)

  val bundleDependencies: List[AnyBundle] = List(
    instructionsBundle,
    LogUploaderBundle(config, scheduler)
  )

  def instructions: AnyInstructions = LazyTry {
    new DataProcessor(config, instructionsBundle).runLoop
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
  val instructionsBundle: AnyDataProcessingBundle
) extends LazyLogging {

  final val workingDir: File = file"/media/ephemeral0/applicator/loquat"

  lazy val aws = instanceAWSClients(config)

  // FIXME: don't use Option.get
  val inputQueue = aws.sqs.getQueueByName(config.resourceNames.inputQueue).get
  val errorQueue = aws.sqs.getQueueByName(config.resourceNames.errorQueue).get
  val outputQueue = aws.sqs.getQueueByName(config.resourceNames.outputQueue).get

  val instance = aws.ec2.getCurrentInstance

  @volatile var stopped = false

  def waitForDataMapping(queue: Queue): Message = {

    var message: Option[Message] = queue.receiveMessage

    while(message.isEmpty) {
      logger.info("Data processor is waiting for new data")
      // instance.foreach(_.createTag(StatusTag.idle))
      Thread.sleep(10.seconds.toMillis)
      message = queue.receiveMessage
    }

    message.get
  }

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
        terminateWorker
        Failure(s"Timeout: ${timeSpent} > taskProcessingTimeout")
      } else {
        futureResult.value match {
          case None => {
            // every 5min we extend it for 6min
            if (tries % (5*60) == 0) {
              if (Try(message.changeVisibilityTimeout(6*60)).isFailure)
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

  def terminateWorker(): Unit = {
    stopped = true
    // instance.foreach(_.createTag(StatusTag.terminating))
    logger.info("Terminating instance")
    instance.foreach(_.terminate)
  }

  private def processDataMapping(
    transferManager: TransferManager,
    dataMapping: SimpleDataMapping,
    workingDir: File
  ): AnyResult = {

    try {

      logger.info("Preparing dataMapping input")

      val inputDir = workingDir / "input"

      val inputFiles: Map[String, File] = dataMapping.inputs.map { case (name, resource) =>

        logger.debug(s"Trying to create input object [${name}]")

        resource match {
          case MessageResource(msg) => {
            val destination: File = inputDir / name
            destination.createIfNotExists().overwrite(msg)
            (name -> destination)
          }
          case S3Resource(s3Address) => {
            // FIXME: this shouldn't ignore the returned Try
            // val destination: File = transferManager.download(s3Address, inputDir / name).get

            val destination: File = (inputDir / name).createIfNotExists()
            val request = new GetObjectRequest(s3Address.bucket, s3Address.key)
            aws.s3.s3.getObject(request, destination.toJava)
            (name -> destination)
          }
        }
      }

      logger.info("Processing data in: " + workingDir.path)
      val result = instructionsBundle.runProcess(workingDir, inputFiles)

      val resultDescription = ProcessingResult(dataMapping.id, result.toString)

      result match {
        case Failure(tr) => {
          logger.error(s"Script finished with non zero code: ${result}. publishing it to the error queue.")
          errorQueue.sendMessage(upickle.default.write(resultDescription))
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

            val uploadTries = outputMap map { case (file, s3Address) =>
              logger.info(s"Publishing output object: [${file.name}]")
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
            }

            transferManager.shutdownNow(false)
            // TODO: check whether we can fold Try's here somehow
            if (uploadTries.forall(_.isSuccess)) {
              logger.info("Finished uploading output files. publishing message to the output queue.")
              outputQueue.sendMessage(upickle.default.write(resultDescription))
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
        logger.error("Fatal failure during dataMapping processing", t)
        Failure(Seq(t.getMessage))
      }
    }
  }

  def runLoop(): Unit = {

    logger.info("DataProcessor started at " + instance.map(_.getInstanceId))


    logger.info("Creating working directory: " + workingDir.path)
    workingDir.createDirectories()

    while(!stopped) {
      try {
        val transferManager = new TransferManager(aws.s3.s3)

        val message = waitForDataMapping(inputQueue)

        // instance.foreach(_.createTag(StatusTag.processing))
        logger.info("DataProcessor: received message " + message)
        val dataMapping = upickle.default.read[SimpleDataMapping](message.body)

        logger.info("DataProcessor processing message")
        import scala.concurrent.ExecutionContext.Implicits._
        val futureResult = Future {
          processDataMapping(transferManager, dataMapping, workingDir)
        }

        // NOTE: this is blocking until the Future gets a result
        val dataMappingResult = waitForResult(futureResult, message)

        logger.debug("Clearing working directory: " + workingDir.path)
        workingDir.clear()

        // logger.info(s"time spent on the task [${dataMapping.id}]: ${timeSpent}")

        // FIXME: check this. what happens if result has failures?
        if (dataMappingResult.isSuccessful) {
          logger.info("Result was successful. deleting message from the input queue")
          inputQueue.deleteMessage(message)
        }

      } catch {
        case e: Throwable => {
          logger.error(s"This instance will terminated due to a fatal error: ${e.getMessage}")
          errorQueue.sendMessage(upickle.default.write(e.getMessage))
          terminateWorker
        }
      }
    }
  }

}
