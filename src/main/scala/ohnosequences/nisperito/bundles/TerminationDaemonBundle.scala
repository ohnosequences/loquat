package ohnosequences.nisperito.bundles

import ohnosequences.nisperito._, pipas._

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._

import com.typesafe.scalalogging.LazyLogging
import scala.collection.mutable.ListBuffer

import ohnosequences.awstools.AWSClients
import com.amazonaws.auth.InstanceProfileCredentialsProvider


case class SNSMessage(Message: String)

case class TerminationDaemonBundle(val config: AnyNisperitoConfig) extends Bundle() with LazyLogging {

  lazy val aws: AWSClients = AWSClients.create(new InstanceProfileCredentialsProvider())

  val TIMEOUT = 300 //5 min

  val successResults = scala.collection.mutable.HashMap[String, String]()
  val failedResults = scala.collection.mutable.HashMap[String, String]()

  object TerminationDaemonThread extends Thread("TerminationDaemonBundle") {


    override def run() {
      logger.info("TerminationDaemonBundle started")

      while(true) {
        logger.info("TerminationDeaemon conditions: " + config.terminationConfig)
        logger.info("TerminationDeaemon success results: " + successResults.size)
        logger.info("TerminationDeaemon failed results: " + failedResults.size)

        receivePipasResults(config.resourceNames.outputQueue).foreach { case (handle, result) =>
          successResults.put(result.id, result.message)
        }

        receivePipasResults(config.resourceNames.errorQueue).foreach { case (handle, result) =>
          failedResults.put(result.id, result.message)
        }

        val reason = checkConditions(
          terminationConfig = config.terminationConfig,
          successResultsCount = successResults.size,
          failedResultsCount = failedResults.size,
          initialPipasCount = config.pipas.length
        )

        reason match {
          case Some(r) => NisperitoOps.undeploy(config)
          case None => ()
        }

        Thread.sleep(TIMEOUT * 1000)
      }

    }
  }

  def receivePipasResults(queueName: String): List[(String, PipaResultDescription)] = {
    val rawMessages: List[(String, String)] = getQueueMessagesWithHandles(queueName)

    rawMessages.map {
      case (handle, rawMessageBody) =>  {
        logger.info(s"rawMessageBody: ${rawMessageBody}")
        // val descriptionMessage: String = upickle.json.write(upickle.json.read(rawMessageBody)("Message"))
        // logger.info(s"descriptionMessage: ${descriptionMessage}")
        // val fixJson = descriptionMessage.replace("\\\"", "\"")
        // logger.info(s"fixJson: ${fixJson}")
        // val description: PipaResultDescription = upickle.default.read[PipaResultDescription](fixJson)
        val snsMessage: SNSMessage = upickle.default.read[SNSMessage](rawMessageBody)
        logger.info(s"snsMessage: ${snsMessage}")
        val r: PipaResultDescription = upickle.default.read[PipaResultDescription](snsMessage.Message.replace("\\\"", "\""))
        logger.info(s"r: ${r}")
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

  def checkConditions(
    terminationConfig: TerminationConfig,
    successResultsCount: Int,
    failedResultsCount: Int,
    initialPipasCount: Int
  ): Option[String] = {

    val startTime = aws.as.getCreatedTime(config.managerAutoScalingGroup.name).map(_.getTime)

    if (
      terminationConfig.terminateAfterInitialPipas &&
      (successResultsCount >= initialPipasCount)
    ) {
      Some("terminated due to terminateAfterInitialPipas: initialPipas count: " + initialPipasCount + " current: " + successResultsCount)
    } else if (
      terminationConfig.errorsThreshold.map{ failedResultsCount >= _ }.getOrElse(false)
    ) {
      Some("terminated due to errorsThreshold: errorsThreshold count: " + terminationConfig.errorsThreshold.get + " current: " + failedResultsCount)
    } else {
      // TODO: check this
      (startTime, terminationConfig.timeout) match {
        case (None, _) => Some("start timeout is undefined!")
        case (Some(timestamp), Some(timeout)) if ((System.currentTimeMillis() - timestamp) > timeout) => {
          Some("terminated due to global timeout!")
        }
        case _ => None
      }
    }
  }

  def install: Results = {
    success("TerminationDaemonBundle installed")
  }

}
