package ohnosequences.loquat

import dataMappings._, dataProcessing._, daemons._, configs._, utils._

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._
import ohnosequences.statika.results._

import ohnosequences.awstools.sqs.Message
import ohnosequences.awstools.sqs.Queue
import ohnosequences.awstools.s3._
import ohnosequences.awstools.AWSClients
import com.typesafe.scalalogging.LazyLogging
import java.io.File
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try
import upickle.Js

import ohnosequences.awstools.AWSClients
import com.amazonaws.auth.InstanceProfileCredentialsProvider
import com.amazonaws.services.s3.transfer._


trait AnyWorkerBundle extends AnyBundle {

  type DataProcessingBundle <: AnyDataProcessingBundle
  val  instructionsBundle: DataProcessingBundle

  type Config <: AnyLoquatConfig
  val  config: Config

  val bundleDependencies: List[AnyBundle] = List(instructionsBundle, LogUploaderBundle(config))

  def instructions: AnyInstructions = {
    LazyTry {
      new DataProcessor(config, instructionsBundle).runLoop
    } -&- say("worker installed")
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

  lazy val aws: AWSClients = AWSClients.create(new InstanceProfileCredentialsProvider())

  // FIXME: don't use Option.get
  val inputQueue = aws.sqs.getQueueByName(config.resourceNames.inputQueue).get
  val errorQueue = aws.sqs.getQueueByName(config.resourceNames.errorQueue).get
  val outputQueue = aws.sqs.getQueueByName(config.resourceNames.outputQueue).get

  val MESSAGE_TIMEOUT = 5.seconds

  val instance = aws.ec2.getCurrentInstance

  @volatile var stopped = false

  def waitForDataMapping(queue: Queue): Message = {

    var message: Option[Message] = queue.receiveMessage

    while(message.isEmpty) {
      logger.info("DataProcessor wait for dataMapping")
      instance.foreach(_.createTag(utils.InstanceTags.IDLE))
      Thread.sleep(MESSAGE_TIMEOUT.toMillis)
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
    instance.foreach(_.createTag(utils.InstanceTags.FINISHING))
    logger.info("terminating")
    instance.foreach(_.terminate)
  }

  def processDataMapping(dataMapping: SimpleDataMapping, workingDir: File): AnyResult = {
    try {
      logger.info("cleaning working directory: " + workingDir.getAbsolutePath)
      utils.deleteRecursively(workingDir)
      logger.info("creating working directory: " + workingDir.getAbsolutePath)
      workingDir.mkdir

      val inputDir = new File(workingDir, "input")
      logger.info("cleaning input directory: " + inputDir.getAbsolutePath)
      utils.deleteRecursively(inputDir)
      inputDir.mkdir

      val outputDir = new File(workingDir, "output")
      logger.info("cleaning output directory: " + outputDir.getAbsolutePath)
      utils.deleteRecursively(outputDir)
      outputDir.mkdir


      val transferManager = new TransferManager(aws.s3.s3)

      logger.info("downloading dataMapping input")
      val inputFilesMap: Map[String, File] = dataMapping.inputs.map {
        case (name, s3Address) =>
          val destination = inputDir / name
          logger.info("trying to create input object: " + name)
          // first we try to download it as a normal object:
          s3Address match {
            case S3Object(bucket, key) => {
              println(s"""Dowloading object
                |from: ${s3Address.url}
                |to: ${destination.path}
                |""".stripMargin)
              transferManager.download(bucket, key, destination).waitForCompletion
              (name -> destination.javaFile)
            }
            case S3Folder(bucket, key) => {
              val fullDestination = destination / key
              println(s"""Dowloading folder
                |from: ${s3Address.url}
                |to: ${fullDestination.path}
                |""".stripMargin)
              transferManager.downloadDirectory(bucket, key, destination).waitForCompletion
              (name -> fullDestination.javaFile)
            }
          }
      }

      logger.info("processing data in: " + workingDir.getAbsolutePath)
      val result = instructionsBundle.processFiles(dataMapping.id, inputFilesMap, workingDir)

      val resultDescription = ProcessingResult(dataMapping.id, result.toString)

      result match {
        case Failure(tr) => {
          logger.error(s"script finished with non zero code: ${result}. publishing it to the error queue.")
          errorQueue.sendMessage(upickle.default.write(resultDescription))
          result
        }
        case Success(tr, outputFileMap) => {
          // FIXME: do it more careful
          val outputMap: Map[File, AnyS3Address] =
            outputFileMap.map { case (name, file) =>
              file -> dataMapping.outputs(name)
            }
          // TODO: simplify this huge if-else statement
          if (outputMap.keys.forall(_.exists)) {

            val uploadTries = outputMap map { case (file, objectAddress) =>
              logger.info(s"publishing output object: ${file} -> ${objectAddress}")

              if (file.isDirectory) Try {
                transferManager.uploadDirectory(
                  objectAddress.bucket,
                  objectAddress.key,
                  file,
                  true // includeSubdirectories
                ).waitForCompletion
              }
              else Try {
                transferManager.upload(
                  objectAddress.bucket,
                  objectAddress.key,
                  file
                ).waitForCompletion
              }
            }

            // TODO: check whether we can fold Try's here somehow
            if (uploadTries.forall(_.isSuccess)) {
              logger.info("finished uploading output files. publishing message to the output queue.")
              outputQueue.sendMessage(upickle.default.write(resultDescription))
              result //-&- success(s"task [${dataMapping.id}] is successfully finished", ())
            } else {
              logger.error(s"some uploads failed: ${uploadTries.filter(_.isFailure)}")
              tr +: Failure(Seq("failed to upload output files"))
            }

          } else {

            val missingFiles = outputMap.keys.filterNot(_.exists).map(_.getCanonicalPath)
            logger.error(s"some output files don't exist: ${missingFiles}")
            tr +: Failure(Seq(s"Couldn't upload results, because some output files don't exist: ${missingFiles}"))

          }
        }
      }
    } catch {
      case t: Throwable => {
        logger.error("fatal failure during dataMapping processing", t)
        Failure(Seq(t.getMessage))
      }
    }
  }

  def runLoop(): Unit = {

    logger.info("DataProcessor started at " + instance.map(_.getInstanceId))

    while(!stopped) {
      var dataMappingId: String = ""
      var lastTimeSpent = 0
      try {
        val message = waitForDataMapping(inputQueue)

        instance.foreach(_.createTag(utils.InstanceTags.PROCESSING))
        logger.info("DataProcessor: received message " + message)
        val dataMapping = upickle.default.read[SimpleDataMapping](message.body)
        dataMappingId = dataMapping.id

        logger.info("DataProcessor processing message")

        import scala.concurrent.ExecutionContext.Implicits._
        val futureResult = Future {
          processDataMapping(dataMapping, config.workingDir)
        }

        val dataMappingResult = waitForResult(futureResult, message)

        // logger.info(s"time spent on the task [${dataMapping.id}]: ${timeSpent}")

        // FIXME: check this. what happens if result has failures?
        if (dataMappingResult.isSuccessful) {
          logger.info("result was successful. deleting message from the input queue")
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
