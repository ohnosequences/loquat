package ohnosequences.loquat

import utils._

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._
import ohnosequences.statika.results._

import ohnosequences.awstools.sqs.Message
import ohnosequences.awstools.sqs.Queue
import ohnosequences.awstools.s3._
import ohnosequences.awstools.AWSClients
import com.typesafe.scalalogging.LazyLogging
import better.files._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try
import upickle.Js

import com.amazonaws.auth.InstanceProfileCredentialsProvider
import com.amazonaws.services.s3.transfer._
import com.amazonaws.services.s3.model.ObjectMetadata


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

  lazy val aws: AWSClients = AWSClients.create(new InstanceProfileCredentialsProvider())

  // FIXME: don't use Option.get
  val inputQueue = aws.sqs.getQueueByName(config.resourceNames.inputQueue).get
  val errorQueue = aws.sqs.getQueueByName(config.resourceNames.errorQueue).get
  val outputQueue = aws.sqs.getQueueByName(config.resourceNames.outputQueue).get

  val instance = aws.ec2.getCurrentInstance

  @volatile var stopped = false

  def waitForDataMapping(queue: Queue): Message = {

    var message: Option[Message] = queue.receiveMessage

    while(message.isEmpty) {
      logger.info("DataProcessor wait for dataMapping")
      instance.foreach(_.createTag(utils.InstanceTags.IDLE))
      Thread.sleep(5.seconds.toMillis)
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

  private def processDataMapping(dataMapping: SimpleDataMapping, workingDir: File): AnyResult = {
    try {
      if(workingDir.exists) {
        logger.info("deleting working directory: " + workingDir.path)
        workingDir.delete(true)
      }
      logger.info("creating working directory: " + workingDir.path)
      workingDir.createDirectories()

      val inputDir = workingDir / "input"
      logger.info("creating input directory: " + workingDir.path)
      inputDir.createDirectories()

      val outputDir = workingDir / "output"
      logger.info("creating output directory: " + workingDir.path)
      outputDir.createDirectories()


      val transferManager = new TransferManager(aws.s3.s3)

      logger.info("downloading dataMapping input")
      val inputFilesMap: Map[String, File] = dataMapping.inputs.map {
        case (name, s3Address) =>
          logger.info("trying to create input object: " + name)
          // FIXME: this shouldn't ignore the returned Try
          val destination: File = transferManager.download(s3Address, inputDir / name).get
          (name -> destination)
      }

      logger.info("processing data in: " + workingDir.path)
      val result = instructionsBundle.runProcess(workingDir, inputFilesMap)

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

            val uploadTries = outputMap map { case (file, s3Address) =>
              logger.info(s"publishing output object: ${file} -> ${s3Address}")
              transferManager.upload(
                file,
                s3Address,
                Map(
                  "artifactName"    -> config.metadata.artifact,
                  "artifactVersion" -> config.metadata.version,
                  "artifactUrl"     -> config.metadata.artifactUrl
                )
              )
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

            val missingFiles = outputMap.keys.filterNot(_.exists).map(_.path)
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
