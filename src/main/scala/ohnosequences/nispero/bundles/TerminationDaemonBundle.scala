package ohnosequences.nisperito.bundles

import ohnosequences.nisperito._

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._

import org.clapper.avsl.Logger
import scala.collection.mutable.ListBuffer


case class SNSMessage(Message: String)

case class TerminationDaemonBundle(val resources: AnyResourcesBundle) extends Bundle(resources) {

  val aws = resources.aws

  val logger = Logger(this.getClass)
  val config = resources.config

  val TIMEOUT = 300 //5 min

  val successResults = scala.collection.mutable.HashMap[String, String]()
  val failedResults = scala.collection.mutable.HashMap[String, String]()

  object TerminationDaemonThread extends Thread("TerminationDaemonBundle") {


    override def run() {
      logger.info("TerminationDaemonBundle started")
      val initialTasksCount: Option[Int] = calcInitialTasksCount()

      while(true) {
        logger.info("TerminationDeaemon conditions: " + config.terminationConfig)
        logger.info("TerminationDeaemon success results: " + successResults.size)
        logger.info("TerminationDeaemon failed results: " + failedResults.size)

        receiveTasksResults(config.resourceNames.outputQueue).foreach { case (handle, result) =>
          successResults.put(result.id, result.message)
        }

        receiveTasksResults(config.resourceNames.errorQueue).foreach { case (handle, result) =>
          failedResults.put(handle, result.message)
        }

        val reason = checkConditions(
          terminationConfig = config.terminationConfig,
          successResultsCount = successResults.size,
          failedResultsCount = failedResults.size,
          initialTasksCount = initialTasksCount
        )

        reason match {
          case Some(r) => Nisperito.undeploy(config)
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
    aws.sqs.getQueueByName(queueName) match {
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

  def checkConditions(terminationConfig: TerminationConfig, successResultsCount: Int, failedResultsCount: Int, initialTasksCount: Option[Int]): Option[String] = {
    val startTime = aws.as.getCreatedTime(config.managerAutoScalingGroup.name).map(_.getTime)

    if (terminationConfig.terminateAfterInitialTasks && initialTasksCount.isDefined && (successResultsCount >= initialTasksCount.get)) {
      Some("terminated due to terminateAfterInitialTasks: initialTasks count: " + initialTasksCount.get + " current: " + successResultsCount)
    } else if (terminationConfig.errorsThreshold.isDefined && (failedResultsCount >= terminationConfig.errorsThreshold.get)) {
      Some("terminated due to errorsThreshold: errorsThreshold count: " + terminationConfig.errorsThreshold.get + " current: " + failedResultsCount)
    } else {
      (startTime, terminationConfig.timeout) match {
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
      val tasksString = aws.s3.readWholeObject(config.initialTasks)
      val tasks: List[AnyTask] = upickle.default.read[List[AnyTask]](tasksString)
      val ids = scala.collection.mutable.HashSet() ++ tasks
      Some(ids.size)
    } catch {
      case t: Throwable => print("warning: couldn't calc initial tasks count!"); None
    }
  }

  def install: Results = {
    success("TerminationDaemonBundle installed")
  }

}
