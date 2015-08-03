package ohnosequences.nispero.bundles

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._
import ohnosequences.awstools.sqs.{Message, Queue}
import ohnosequences.nispero._
import ohnosequences.nispero.manager._
import org.clapper.avsl.Logger
import ohnosequences.nispero.utils.pickles._
import upickle._, default._

import ohnosequences.awstools.sqs.Message
import ohnosequences.awstools.sqs.Queue


abstract class ControlQueueHandler(resourcesBundle: Resources, aws: AWS) extends Bundle(resourcesBundle, aws) {

  val logger = Logger(this.getClass)

  def waitForTask(queue: Queue): Message = {

    logger.info("waiting for command")

    val MESSAGE_TIMEOUT = 5000

    queue.receiveMessage match {
      case Some(message) => message
      case None => {
        Thread.sleep(MESSAGE_TIMEOUT)
        waitForTask(queue)
      }
    }
  }

  def run() {
    val config = resourcesBundle.config
    val controlQueue = aws.clients.sqs.getQueueByName(resourcesBundle.resources.controlQueue).get
    val inputQueue =  aws.clients.sqs.getQueueByName(resourcesBundle.resources.inputQueue).get

    while(true) {
      try {
        val message = waitForTask(controlQueue)

        val command: RawCommand = read[RawCommand](message.body)
        logger.info("received command: " + command)
        command match {
          case RawCommand("UnDeploy", reason: String) => {
            Undeployer.undeploy(aws.clients, config, reason)
          }
          case RawCommand("AddTasks", tasks: String) => {
            val parsedTasks = upickle.default.read[List[AnyTask]](tasks)
            parsedTasks.foreach { task =>
              inputQueue.sendMessage(upickle.default.write(task))
            }
          }
          case RawCommand("ChangeCapacity", n: String) => {
            aws.clients.as.setDesiredCapacity(config.resources.workersGroup, n.toInt)
          }
        }
        controlQueue.deleteMessage(message)

      } catch {
        case t: Throwable => {
          logger.error("error during handling command from control queue")
          t.printStackTrace()
        }
      }
    }
  }

  def install: Results = {
    success("ControlQueueHandler installed")
  }

}
