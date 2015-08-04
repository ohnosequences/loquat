package ohnosequences.nispero.bundles

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._
import ohnosequences.awstools.sqs.{Message, Queue}
import ohnosequences.nispero._
import org.clapper.avsl.Logger
import ohnosequences.nispero.utils.pickles._
import upickle._, default._

import ohnosequences.awstools.sqs.Message
import ohnosequences.awstools.sqs.Queue

// NOTE: probably this whole thing can be safely removed (I guess it is related to the web-console)
case object commands {

  sealed trait AnyRawCommand {
    val command: String
    val arg: String
  }
  // abstract class RawCommand(val command: String, val arg: String) extends AnyRawCommand

  case class UnDeploy(reason: String) extends AnyRawCommand {
    val command = "UnDeploy"
    val arg = reason
  }
  case class AddTasks(tasks: List[AnyTask]) extends AnyRawCommand {
    val command = "AddTasks"
    val arg = tasks.toString()
  }
  case class ChangeCapacity(capacity: Int) extends AnyRawCommand {
    val command = "ChangeCapacity"
    val arg = capacity.toString
  }
}


abstract class ControlQueueBundle(resources: AnyResourcesBundle) extends Bundle(resources) {
  import commands._

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
    val config = resources.config
    val aws = resources.aws
    val controlQueue = aws.sqs.getQueueByName(config.resourceNames.controlQueue).get
    val inputQueue =  aws.sqs.getQueueByName(config.resourceNames.inputQueue).get

    while(true) {
      try {
        val message = waitForTask(controlQueue)

        logger.info("received command: " + message.body)
        val command = read[AnyRawCommand](message.body)
        command match {
          case UnDeploy(reason: String) => {
            Undeployer.undeploy(aws, config, reason)
          }
          case AddTasks(tasks: List[AnyTask]) => {
            tasks.foreach { task =>
              inputQueue.sendMessage(upickle.default.write(task))
            }
          }
          case ChangeCapacity(n: Int) => {
            aws.as.setDesiredCapacity(config.workersAutoScalingGroup, n)
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
    success("ControlQueueBundle installed")
  }

}
