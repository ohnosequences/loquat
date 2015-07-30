package ohnosequences.nispero.bundles

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._
import org.clapper.avsl.Logger
import ohnosequences.nispero._
import scala.collection.mutable.ListBuffer
import scala.Some
import ohnosequences.nispero.TaskResultDescription
import ohnosequences.nispero.Task
import ohnosequences.nispero.utils.pickles._
import upickle._

case class SNSMessage(Message: String)

abstract class TerminationDaemon(resourcesBundle: Resources, aws: AWS) extends Bundle(resourcesBundle, aws) {

  val logger = Logger(this.getClass)
  val config = resourcesBundle.config

  val TIMEOUT = 300 //5 min

  val successResults = scala.collection.mutable.HashMap[String, String]()
  val failedResults = scala.collection.mutable.HashMap[String, String]()

  object TerminationDaemonThread extends Thread("TerminationDaemon") {


    override def run() {
      logger.info("TerminationDaemon started")
      val initialTasksCount: Option[Int] = calcInitialTasksCount()

      while(true) {
        logger.info("TerminationDeaemon conditions: " + config.terminationConditions)
        logger.info("TerminationDeaemon success results: " + successResults.size)
        logger.info("TerminationDeaemon failed results: " + failedResults.size)

        receiveTasksResults(config.resources.outputQueue).foreach { case (handle, result) =>
          successResults.put(result.id, result.message)
        }

        receiveTasksResults(config.resources.errorQueue).foreach { case (handle, result) =>
          failedResults.put(handle, result.message)
        }

        val reason = checkConditions(
          terminationConditions = config.terminationConditions,
          successResultsCount = successResults.size,
          failedResultsCount = failedResults.size,
          initialTasksCount = initialTasksCount
        )

        reason match {
          case Some(r) => Undeployer.undeploy(aws.clients, config, r)
          case None => ()
        }

        Thread.sleep(TIMEOUT * 1000)
      }

    }
  }

  def receiveTasksResults(queueName: String): List[(String, TaskResultDescription)] = {
    val rawMessages: List[(String, String)] = getQueueMessagesWithHandles(queueName)

    rawMessages.map {
      case (handle, rawMessageBody) =>  {
        val snsMessage: SNSMessage = upickle.default.read[SNSMessage](rawMessageBody)
        val r: TaskResultDescription = upickle.default.read[TaskResultDescription](snsMessage.Message)
        (handle, r)
      }
    }
  }

  def getQueueMessagesWithHandles(queueName: String): List[(String, String)] = {
    aws.clients.sqs.getQueueByName(queueName) match {
      case None => Nil
      case Some(queue) => {
        var messages = ListBuffer[(String, String)]()
        var empty = false
        var i = 0
        while(!empty && i < 10) {
          val chuck = queue.receiveMessages(10)
          if (chuck.isEmpty) {
            empty = true
          }
          messages ++= chuck.map { m=>
            m.receiptHandle -> m.body
          }
          i += 1
        }
        messages.toList
      }
    }
  }

  def checkConditions(terminationConditions: TerminationConditions, successResultsCount: Int, failedResultsCount: Int, initialTasksCount: Option[Int]): Option[String] = {
    val startTime = aws.clients.as.getCreatedTime(config.managerConfig.groups._1.name).map(_.getTime)

    if (terminationConditions.terminateAfterInitialTasks && initialTasksCount.isDefined && (successResultsCount >= initialTasksCount.get)) {
      Some("terminated due to terminateAfterInitialTasks: initialTasks count: " + initialTasksCount.get + " current: " + successResultsCount)
    } else if (terminationConditions.errorsThreshold.isDefined && (failedResultsCount >= terminationConditions.errorsThreshold.get)) {
      Some("terminated due to errorsThreshold: errorsThreshold count: " + terminationConditions.errorsThreshold.get + " current: " + failedResultsCount)
    } else {
      (startTime, terminationConditions.timeout) match {
        case (None, _) => Some("start timeout is undefined!")
        case (Some(timestamp), Some(timeout)) if ((System.currentTimeMillis() - timestamp) > timeout) => {
          Some("terminated due to global timeout!")
        }
        case _ => None
      }
    }
  }


  def calcInitialTasksCount(): Option[Int] = {
    try {
      val tasksString = aws.clients.s3.readWholeObject(config.initialTasks)
      val tasks: List[Task] = upickle.default.read[List[Task]](tasksString)
      val ids = scala.collection.mutable.HashSet() ++ tasks
      Some(ids.size)
    } catch {
      case t: Throwable => print("warning: couldn't calc initial tasks count!"); None
    }
  }

  def install: Results = {
    success("TerminationDaemon installed")
  }

}