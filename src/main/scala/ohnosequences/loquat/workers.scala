package ohnosequences.loquat

import dataMappings._, instructions._, daemons._, configs._, utils._

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._
import ohnosequences.statika.results._

import ohnosequences.awstools.sqs.Message
import ohnosequences.awstools.sqs.Queue
import ohnosequences.awstools.s3.ObjectAddress
import ohnosequences.awstools.AWSClients
import com.typesafe.scalalogging.LazyLogging
import java.io.File
import scala.concurrent.Future
import scala.util.Try
import upickle.Js

import ohnosequences.awstools.AWSClients
import com.amazonaws.auth.InstanceProfileCredentialsProvider


trait AnyWorkerBundle extends AnyBundle {

  type InstructionsBundle <: AnyInstructionsBundle
  val  instructionsBundle: InstructionsBundle

  type Config <: AnyLoquatConfig
  val  config: Config

  val bundleDependencies: List[AnyBundle] = List(instructionsBundle, LogUploaderBundle(config))

  def instructions: AnyInstructions = {
    Try {
      new InstructionsExecutor(config, instructionsBundle).runLoop
    } -&- say("worker installed")
  }
}

abstract class WorkerBundle[
  I <: AnyInstructionsBundle,
  C <: AnyLoquatConfig
](val instructionsBundle: I,
  val config: C
) extends AnyWorkerBundle {

  type InstructionsBundle = I
  type Config = C
}


// TODO: rewrite all this and make it Worker's install
class InstructionsExecutor(
  val config: AnyLoquatConfig,
  val instructionsBundle: AnyInstructionsBundle
) extends LazyLogging {

  lazy val aws: AWSClients = AWSClients.create(new InstanceProfileCredentialsProvider())

  // FIXME: don't use Option.get
  val inputQueue = aws.sqs.getQueueByName(config.resourceNames.inputQueue).get
  val errorQueue = aws.sqs.getQueueByName(config.resourceNames.errorQueue).get
  val outputQueue = aws.sqs.getQueueByName(config.resourceNames.outputQueue).get

  val MESSAGE_TIMEOUT = 5000

  val instance = aws.ec2.getCurrentInstance

  @volatile var stopped = false

  def waitForDataMapping(queue: Queue): Message = {

    var message: Option[Message] = queue.receiveMessage

    while(message.isEmpty) {
      logger.info("InstructionsExecutor wait for dataMapping")
      instance.foreach(_.createTag(utils.InstanceTags.IDLE))
      Thread.sleep(MESSAGE_TIMEOUT)
      message = queue.receiveMessage
    }

    message.get
  }

  def waitForResult[R <: AnyResult](futureResult: Future[R], message: Message): Result[Int] = {
    val startTime = System.currentTimeMillis()
    val step = 1000 // 1s

    def timeSpent(): Int = {
      val currentTime = System.currentTimeMillis()
      ((currentTime - startTime) / 1000).toInt
    }

    @scala.annotation.tailrec
    def waitMore(tries: Int): AnyResult = {
      if(timeSpent > config.terminationConfig.taskProcessingTimeout.getOrElse(Hours(12)).inSeconds) {
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
            Thread.sleep(step)
            logger.info("Solving dataMapping: " + utils.printInterval(timeSpent()))
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


      logger.info("downloading dataMapping input")
      val loadingManager = aws.s3.createLoadingManager

      val inputFilesMap: Map[String, File] = dataMapping.inputs.map {
        case (name, objectAddress) =>
          val inputFile = new File(inputDir, name)
          logger.info("trying to create input object: " + name)
          loadingManager.download(objectAddress, inputFile)
          (name -> inputFile)
      }

      logger.info("running instructions script in " + workingDir.getAbsolutePath)
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
          val outputMap: Map[File, ObjectAddress] =
            outputFileMap.map { case (name, file) =>
              file -> dataMapping.outputs(name)
            }
          // TODO: simplify this huge if-else statement
          if (outputMap.keys.forall(_.exists)) {

            val uploadTries = outputMap map { case (file, objectAddress) =>
              logger.info(s"publishing output object: ${file} -> ${objectAddress}")
              // TODO: publicity should be a configurable option
              aws.s3.uploadFile(objectAddress / file.getName, file, public = true)
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

            logger.error("some output files don't exist!")
            tr +: Failure(Seq("Couldn't upload results, because some output files don't exist"))

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

    logger.info("InstructionsExecutor started at " + instance.map(_.getInstanceId))

    while(!stopped) {
      var dataMappingId: String = ""
      var lastTimeSpent = 0
      try {
        val message = waitForDataMapping(inputQueue)

        instance.foreach(_.createTag(utils.InstanceTags.PROCESSING))
        logger.info("InstructionsExecutor: received message " + message)
        val dataMapping = upickle.default.read[SimpleDataMapping](message.body)
        dataMappingId = dataMapping.id

        logger.info("InstructionsExecutor processing message")

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
