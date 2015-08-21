package ohnosequences.loquat

import dataMappings._, instructions._, daemons._

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._

import ohnosequences.awstools.sqs.Message
import ohnosequences.awstools.sqs.Queue
import ohnosequences.awstools.s3.ObjectAddress
import ohnosequences.awstools.AWSClients
import com.typesafe.scalalogging.LazyLogging
import java.io.File
import scala.concurrent.Future
import upickle.Js

import ohnosequences.awstools.AWSClients
import com.amazonaws.auth.InstanceProfileCredentialsProvider


trait AnyWorkerBundle extends AnyBundle {

  type Instructions <: AnyInstructionsBundle
  val  instructions: Instructions

  type Config <: AnyLoquatConfig
  val  config: Config

  val bundleDependencies: List[AnyBundle] = List(instructions, LogUploaderBundle(config))

  def install: Results = {
    InstructionsExecutor(config, instructions).runLoop
    success("worker installed")
  }
}

abstract class WorkerBundle[
  I <: AnyInstructionsBundle,
  C <: AnyLoquatConfig
](val instructions: I,
  val config: C
) extends AnyWorkerBundle {

  type Instructions = I
  type Config = C
}


// TODO: rewrite all this and make it Worker's install
case class InstructionsExecutor(
  val config: AnyLoquatConfig,
  val instructions: AnyInstructionsBundle
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

  def waitForResult(futureResult: Future[Results], message: Message): (Results, Int) = {
    val startTime = System.currentTimeMillis()
    val step = 1000 // 1s

    def timeSpent(): Int = {
      val currentTime = System.currentTimeMillis()
      ((currentTime - startTime) / 1000).toInt
    }

    var stopWaiting = false

    var dataMappingResult: Results = failure("internal error during waiting for dataMapping result")


    var it = 0
    while(!stopWaiting) {
      if(timeSpent() > math.min(config.terminationConfig.dataMappingProcessTimeout, 12 * 60 * 60)) {
        stopWaiting = true
        dataMappingResult = failure("Timeout: " + timeSpent + " > dataMappingProcessTimeout")
        terminateWorker
      } else {
        futureResult.value match {
          case None => {
            try {
              // every 5min we extend it for 6min
              if (it % (5*60) == 0) message.changeVisibilityTimeout(6*60)
            } catch {
              case e: Throwable => logger.info("Couldn't change the visibility timeout")
            }
            Thread.sleep(step)
            logger.info("Solving dataMapping: " + utils.printInterval(timeSpent()))
            it += 1
          }
          case Some(scala.util.Success(r)) => stopWaiting = true; dataMappingResult = r
          case Some(scala.util.Failure(t)) => stopWaiting = true; dataMappingResult = failure("future error: " + t.getMessage)
        }
      }
    }
    (dataMappingResult, timeSpent())
  }

  def terminateWorker(): Unit = {
    stopped = true
    instance.foreach(_.createTag(utils.InstanceTags.FINISHING))
    logger.info("terminating")
    instance.foreach(_.terminate)
  }

  def processDataMapping(dataMapping: SimpleDataMapping, workingDir: File): Results = {
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
      dataMapping.inputs.foreach { case (name, objectAddress) =>
        val inputFile = new File(inputDir, name)
        logger.info("trying to create input object: " + name)
        loadingManager.download(objectAddress, inputFile)
        // (name -> inputFile)
      }

      logger.info("running instructions script in " + workingDir.getAbsolutePath)
      val (result, output) = instructions.processDataMapping(dataMapping.id, workingDir)

      val resultDescription = ProcessingResult(dataMapping.id, result.toString)

      // FIXME: do it more careful
      val outputMap: Map[File, ObjectAddress] =
        instructions.filesMap(output).map { case (name, file) =>
          file -> dataMapping.outputs(name)
        }

      // TODO: simplify this huge if-else statement
      if (result.hasFailures) {

        logger.error(s"script finished with non zero code: ${result}. publishing it to the error queue.")
        errorQueue.sendMessage(upickle.default.write(resultDescription))
        result -&- failure(s"script finished with non zero code")

      } else if (outputMap.keys.forall(_.exists)) {

        val uploadTries = outputMap map { case (file, objectAddress) =>
          logger.info(s"publishing output object: ${file} -> ${objectAddress}")
          // TODO: publicity should be a configurable option
          aws.s3.uploadFile(objectAddress / file.getName, file, public = true)
        }

        // TODO: check whether we can fold Try's here somehow
        if (uploadTries.forall(_.isSuccess)) {
          logger.info("finished uploading output files. publishing message to the output queue.")
          outputQueue.sendMessage(upickle.default.write(resultDescription))
          result -&- success(s"task [${dataMapping.id}] is successfully finished")
        } else {
          logger.error(s"some uploads failed: ${uploadTries.filter(_.isFailure)}")
          result -&- failure("failed to upload output files")
        }

      } else {

        logger.error("some output files don't exist!")
        result -&- failure("Couldn't upload results, because some output files don't exist")

      }
    } catch {
      case t: Throwable => {
        logger.error("fatal failure during dataMapping processing", t)
        failure(t.getMessage)
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

        val (dataMappingResult, timeSpent) = waitForResult(futureResult, message)

        logger.info(s"time spent on the task [${dataMapping.id}]: ${timeSpent}")

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
