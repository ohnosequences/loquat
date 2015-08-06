package ohnosequences.nisperito.bundles

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._

import ohnosequences.nisperito._
import ohnosequences.awstools.sqs.Message
import ohnosequences.awstools.sqs.Queue
import ohnosequences.awstools.s3.ObjectAddress
import ohnosequences.awstools.AWSClients
import org.clapper.avsl.Logger
import java.io.File
import scala.concurrent.Future


trait AnyWorkerBundle extends AnyBundle {

  type Instructions <: AnyInstructionsBundle
  val  instructions: Instructions

  type Resources <: AnyResourcesBundle
  val  resources: Resources

  val logUploader: LogUploaderBundle = LogUploaderBundle(resources)

  val bundleDependencies: List[AnyBundle] = List(instructions, logUploader)

  def install: Results = {
    InstructionsExecutor(resources.config, instructions, resources.aws).runLoop
    success("worker installed")
  }
}

abstract class WorkerBundle[
  I <: AnyInstructionsBundle,
  R <: AnyResourcesBundle
](val instructions: I,
  val resources: R
) extends AnyWorkerBundle {

  type Instructions = I
  type Resources = R
}


// TODO: rewrite all this and make it Worker's install
case class InstructionsExecutor(
  val config: AnyNisperitoConfig,
  val instructions: AnyInstructionsBundle,
  val aws: AWSClients
) {

  val MESSAGE_TIMEOUT = 5000

  val logger = Logger(this.getClass)

  val instance = aws.ec2.getCurrentInstance

  @volatile var stopped = false

  def waitForTask(queue: Queue): Message = {

    var message: Option[Message] = queue.receiveMessage

    while(message.isEmpty) {
      logger.info("InstructionsExecutor wait for task")
      instance.foreach(_.createTag(InstanceTags.IDLE))
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

    var taskResult: Results = failure("internal error during waiting for task result")


    var it = 0
    while(!stopWaiting) {
      if(timeSpent() > min(config.terminationConfig.taskProcessTimeout, 12 * 60 * 60)) {
        stopWaiting = true
        taskResult = failure("Timeout: " + timeSpent + " > taskProcessTimeout")
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
            logger.info("Solving task: " + utils.printInterval(timeSpent()))
            it += 1
          }
          case Some(scala.util.Success(r)) => stopWaiting = true; taskResult = r
          case Some(scala.util.Failure(t)) => stopWaiting = true; taskResult = failure("future error: " + t.getMessage)
        }
      }
    }
    (taskResult, timeSpent())
  }

  def terminateWorker(): Unit = {
    stopped = true
    instance.foreach(_.createTag(InstanceTags.FINISHING))
    logger.info("terminating")
    instance.foreach(_.terminate)
  }

  def processTask(task: AnyTask, workingDir: File): Results = {
    try {
    // val cleanWorkingDir: Results = {
      logger.info("cleaning working directory: " + workingDir.getAbsolutePath)
      utils.deleteRecursively(workingDir)
      logger.info("creating working directory: " + workingDir.getAbsolutePath)
      workingDir.mkdir()

      val inputDir = new File(workingDir, "input")
      logger.info("cleaning input directory: " + inputDir.getAbsolutePath)
      utils.deleteRecursively(inputDir)
      inputDir.mkdir()

      val outputDir = new File(workingDir, "output")
      logger.info("cleaning output directory: " + outputDir.getAbsolutePath)
      utils.deleteRecursively(outputDir)
      outputDir.mkdir()

    //   success("cleaned working directories")
    // }

      logger.info("downloading task input")
      val inputFiles: List[File] = task match {
        /* if it's a tiny task, we just create the files with input messages */
        case TinyTask(_, inputObjs, _) =>
          inputObjs.map { case (name, content: String) =>
            val inputFile = new File(inputDir, name)
            logger.info("trying to create input object: " + name)
            utils.writeStringToFile(content, inputFile)
            inputFile
          }.toList
        /* if it's a big task, we download objects from S3 */
        case BigTask(_, inputObjs, _) =>
          inputObjs.map { case (name, objAddress: ObjectAddress) =>
            val inputFile = new File(inputDir, name)
            logger.info("trying to create input object: " + name)
            aws.s3.createLoadingManager.download(objAddress, inputFile)
            inputFile
          }.toList
      }

      logger.info("running instructions script in " + workingDir.getAbsolutePath)
      val result = instructions.processTask(task, inputFiles, outputDir)

      val messageFile = new File(workingDir, "message")

      val message = if (messageFile.exists()) {
        scala.io.Source.fromFile(messageFile).mkString
      } else {
        logger.warn("couldn't find message file")
        ""
      }

      if (result.hasFailures) {
        logger.error("script finished with non zero code: " + result)
        if (message.isEmpty) {
          failure("script finished with non zero code: " + result)
        } else {
          failure(message)
        }
      } else {
        logger.info("start.sh script finished, uploading results")
        for ((name, objectAddress) <- task.outputObjects) {
          val outputFile = new File(outputDir, name)
          if (outputFile.exists()) {
            logger.info("trying to publish output object " + objectAddress)
            // TODO: publicity should be a configurable option
            aws.s3.putObject(objectAddress, outputFile, public = true)
            logger.info("success")
          } else {
            logger.warn("warning: file " + outputFile.getAbsolutePath + " doesn't exists!")
          }
        }
        success(message)
      }
    } catch {
      case e: Throwable => {
        e.printStackTrace()
        failure(e.getMessage)
      }
    }
  }

  def runLoop(): Unit = {

    logger.info("InstructionsExecutor started at " + instance.map(_.getInstanceId))

    val inputQueue = aws.sqs.getQueueByName(config.resourceNames.inputQueue).get
    val outputTopic = aws.sns.createTopic(config.resourceNames.outputTopic)
    val errorTopic = aws.sns.createTopic(config.resourceNames.errorTopic)

    while(!stopped) {
      var taskId = ""
      var lastTimeSpent = 0
      try {
        val message = waitForTask(inputQueue)

        instance.foreach(_.createTag(InstanceTags.PROCESSING))
        logger.info("InstructionsExecutor: received message " + message)
        val task = upickle.default.read[AnyTask](message.body)
        taskId = task.id

        logger.info("InstructionsExecutor processing message")

        import scala.concurrent.ExecutionContext.Implicits._
        val futureResult = Future {
          processTask(task, config.workersConfig.workingDir)
        }

        val (taskResult, timeSpent) = waitForResult(futureResult, message)
        lastTimeSpent = timeSpent

        logger.info("task result: " + taskResult)

        val taskResultDescription = TaskResultDescription(
          id = task.id,
          message = taskResult.toString,
          instanceId = instance.map(_.getInstanceId()),
          time = timeSpent
        )

        logger.info("publishing result to topic")

        if (taskResult.hasFailures) {
          errorTopic.publish(upickle.default.write(taskResultDescription))
        } else {
          outputTopic.publish(upickle.default.write(taskResultDescription))
          logger.info("InstructionsExecutor deleting message with from input queue")
          inputQueue.deleteMessage(message)
        }
      } catch {
        case e: Throwable =>  {
          logger.error("fatal error instance will terminated")
          e.printStackTrace()
          val taskResultDescription = TaskResultDescription(
            id = taskId,
            message = e.getMessage,
            instanceId = instance.map(_.getInstanceId()),
            time = lastTimeSpent
          )
          errorTopic.publish(upickle.default.write(taskResultDescription))
          terminateWorker
        }
      }
    }
  }

}
