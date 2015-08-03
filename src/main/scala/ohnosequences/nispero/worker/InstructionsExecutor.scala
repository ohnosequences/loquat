package ohnosequences.nispero.worker

import ohnosequences.nispero._
import ohnosequences.nispero.utils.Utils
import ohnosequences.awstools.sqs.Message
import ohnosequences.awstools.sns.Topic
import ohnosequences.awstools.sqs.Queue
import ohnosequences.awstools.AWSClients
import org.clapper.avsl.Logger
import java.io.File
import scala.concurrent.Future
import ohnosequences.nispero.utils.pickles._
import upickle._


class InstructionsExecutor(config: Config, instructions: Instructions, val awsClients: AWSClients) {

  val MESSAGE_TIMEOUT = 5000

  import awsClients._

  val logger = Logger(this.getClass)

  val instance = ec2.getCurrentInstance

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

  def waitForResult(futureResult: Future[TaskResult], message: Message): (TaskResult, Int) = {
    val startTime = System.currentTimeMillis()
    val step = 1000 // 1s

    def timeSpent(): Int = {
      val currentTime = System.currentTimeMillis()
      ((currentTime - startTime) / 1000).toInt
    }

    var stopWaiting = false

    var taskResult: TaskResult = TaskResult.Failure("internal error during waiting for task result")


    var it = 0
    while(!stopWaiting) {
      if(timeSpent() > config.taskProcessTimeout) {
        stopWaiting = true
        taskResult = TaskResult.Failure("Timeout: " + timeSpent + " > taskProcessTimeout")
        terminate()
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
            logger.info("Solving task: " + Utils.printInterval(timeSpent()))
            it += 1
          }
          case Some(scala.util.Success(r)) => stopWaiting = true; taskResult = r
          case Some(scala.util.Failure(t)) => stopWaiting = true; taskResult = TaskResult.Failure("future error: " + t.getMessage)
        }
      }
    }
    (taskResult, timeSpent())
  }

  def terminate() {
    stopped = true
    instance.foreach(_.createTag(InstanceTags.FINISHING))
    logger.info("terminating")

    instance.foreach(_.terminate())

  }


  def run() {

    logger.info("InstructionsExecutor started at " + instance.map(_.getInstanceId))

    val inputQueue = sqs.getQueueByName(config.resources.inputQueue).get
    val outputTopic = sns.createTopic(config.resources.outputTopic)
    val errorTopic = sns.createTopic(config.resources.errorTopic)

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
          instructions.execute(s3, task, new File(config.workersDir))
        }

        val (taskResult, timeSpent) = waitForResult(futureResult, message)
        lastTimeSpent = timeSpent

        logger.info("task result: " + taskResult)

        val taskResultDescription = TaskResultDescription(
          id = task.id,
          message = taskResult.message,
          instanceId = instance.map(_.getInstanceId()),
          time = timeSpent
        )

        logger.info("publishing result to topic")

        taskResult match {
          case TaskResult.Success(msg) => {
            outputTopic.publish(upickle.default.write(taskResultDescription.copy(message = msg)))
            logger.info("InstructionsExecutor deleting message with from input queue")
            inputQueue.deleteMessage(message)
          }
          case TaskResult.Failure(msg) => {
            errorTopic.publish(upickle.default.write(taskResultDescription.copy(message = msg)))
          }
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
          terminate()
        }
      }
    }
  }

}
