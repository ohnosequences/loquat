package ohnosequences.nisperito.bundles

import ohnosequences.nisperito._, pipas._, instructions._

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

  type Config <: AnyNisperitoConfig
  val  config: Config

  val bundleDependencies: List[AnyBundle] = List(instructions, LogUploaderBundle(config))

  def install: Results = {
    InstructionsExecutor(config, instructions).runLoop
    success("worker installed")
  }
}

abstract class WorkerBundle[
  I <: AnyInstructionsBundle,
  C <: AnyNisperitoConfig
](val instructions: I,
  val config: C
) extends AnyWorkerBundle {

  type Instructions = I
  type Config = C
}


// TODO: rewrite all this and make it Worker's install
case class InstructionsExecutor(
  val config: AnyNisperitoConfig,
  val instructions: AnyInstructionsBundle
) extends LazyLogging {

  lazy val aws: AWSClients = AWSClients.create(new InstanceProfileCredentialsProvider())

  val MESSAGE_TIMEOUT = 5000

  val instance = aws.ec2.getCurrentInstance

  @volatile var stopped = false

  def waitForPipa(queue: Queue): Message = {

    var message: Option[Message] = queue.receiveMessage

    while(message.isEmpty) {
      logger.info("InstructionsExecutor wait for pipa")
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

    var pipaResult: Results = failure("internal error during waiting for pipa result")


    var it = 0
    while(!stopWaiting) {
      if(timeSpent() > math.min(config.terminationConfig.pipaProcessTimeout, 12 * 60 * 60)) {
        stopWaiting = true
        pipaResult = failure("Timeout: " + timeSpent + " > pipaProcessTimeout")
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
            logger.info("Solving pipa: " + utils.printInterval(timeSpent()))
            it += 1
          }
          case Some(scala.util.Success(r)) => stopWaiting = true; pipaResult = r
          case Some(scala.util.Failure(t)) => stopWaiting = true; pipaResult = failure("future error: " + t.getMessage)
        }
      }
    }
    (pipaResult, timeSpent())
  }

  def terminateWorker(): Unit = {
    stopped = true
    instance.foreach(_.createTag(InstanceTags.FINISHING))
    logger.info("terminating")
    instance.foreach(_.terminate)
  }

  def processPipa(pipa: SimplePipa, workingDir: File): Results = {
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


      logger.info("downloading pipa input")
      val loadingManager = aws.s3.createLoadingManager
      pipa.inputs.foreach { case (name, objectAddress) =>
        val inputFile = new File(inputDir, name)
        logger.info("trying to create input object: " + name)
        loadingManager.download(objectAddress, inputFile)
        // (name -> inputFile)
      }

      logger.info("running instructions script in " + workingDir.getAbsolutePath)
      val (result, output) = instructions.processPipa(pipa.id, workingDir)

      // FIXME: do it more careful
      val outputMap: Map[File, ObjectAddress] =
        instructions
        .filesMap(output)
        .map { case (name, file) =>
          file -> pipa.outputs(name)
        }

      if (result.hasFailures) {
        logger.error(s"script finished with non zero code: ${result}")
        failure(s"script finished with non zero code: ${result}")
      } else {
        logger.info("pipa finished, uploading results")
        for ((file, objectAddress) <- outputMap) {
          if (file.exists) {
            logger.info(s"trying to publish output object: ${objectAddress}")
            // TODO: publicity should be a configurable option
            aws.s3.uploadFile(objectAddress / file.getName, file, public = true)
            logger.info("success")
          } else {
            logger.error(s"file [${file.getAbsolutePath}] doesn't exists!")
          }
        }
        result -&- success(s"pipa [${pipa.id}] successfully finished")
      }
    } catch {
      case t: Throwable => {
        logger.error("fatal failure during pipa processing", t)
        failure(t.getMessage)
      }
    }
  }

  def runLoop(): Unit = {

    logger.info("InstructionsExecutor started at " + instance.map(_.getInstanceId))

    val inputQueue = aws.sqs.getQueueByName(config.resourceNames.inputQueue).get
    val outputTopic = aws.sns.createTopic(config.resourceNames.outputTopic)
    val errorTopic = aws.sns.createTopic(config.resourceNames.errorTopic)

    while(!stopped) {
      var pipaId: String = ""
      var lastTimeSpent = 0
      try {
        val message = waitForPipa(inputQueue)

        instance.foreach(_.createTag(InstanceTags.PROCESSING))
        logger.info("InstructionsExecutor: received message " + message)
        val pipa = upickle.default.read[SimplePipa](message.body)
        pipaId = pipa.id

        logger.info("InstructionsExecutor processing message")

        import scala.concurrent.ExecutionContext.Implicits._
        val futureResult = Future {
          processPipa(pipa, config.workingDir)
        }

        val (pipaResult, timeSpent) = waitForResult(futureResult, message)
        lastTimeSpent = timeSpent

        logger.info("pipa result: " + pipaResult)

        val pipaResultDescription = PipaResultDescription(
          id = pipaId,
          message = pipaResult.toString,
          instanceId = instance.map(_.getInstanceId()),
          time = timeSpent
        )

        logger.info("publishing result to topic")

        if (pipaResult.hasFailures) {
          errorTopic.publish(upickle.default.write(pipaResultDescription))
        } else {
          outputTopic.publish(upickle.default.write(pipaResultDescription))
          logger.info("InstructionsExecutor deleting message with from input queue")
          inputQueue.deleteMessage(message)
        }
      } catch {
        case e: Throwable =>  {
          logger.error("fatal error! instance will terminated")
          e.printStackTrace()
          val pipaResultDescription = PipaResultDescription(
            id = pipaId,
            message = e.getMessage,
            instanceId = instance.map(_.getInstanceId()),
            time = lastTimeSpent
          )
          errorTopic.publish(upickle.default.write(pipaResultDescription))
          terminateWorker
        }
      }
    }
  }

}
