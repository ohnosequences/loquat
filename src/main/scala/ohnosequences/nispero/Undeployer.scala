package ohnosequences.nispero

import org.clapper.avsl.Logger
import ohnosequences.awstools.AWSClients


object Undeployer {


  def undeploy(aws: AWSClients, config: AnyNisperoConfig, reason: String) {

    val logger = Logger(this.getClass())
    logger.info("termination due to " + reason)
    logger.info("send notification")
    try {
      val message = "termination due to " + reason
      val subject = "Nispero " + config.nisperoId + " terminated"
      val notificationTopic = aws.sns.createTopic(config.notificationTopic)
      notificationTopic.publish(message, subject)
    } catch {
      case t: Throwable => logger.error("error during sending notification" + t.getMessage)
    }


    logger.info("deleting workers group")
    aws.as.deleteAutoScalingGroup(config.workersAutoScalingGroup)


    try {
      logger.info("deleting bucket " + config.resourceNames.bucket)
      aws.s3.deleteBucket(config.resourceNames.bucket)
    } catch {
      case t: Throwable => logger.error("error during deleting bucket: " + t.getMessage)
    }

    try {
      aws.sqs.getQueueByName(config.resourceNames.errorQueue).foreach(_.delete())
    } catch {
      case t: Throwable => logger.error("error during deleting error queue " + t.getMessage)
    }

    try {
      aws.sns.createTopic(config.resourceNames.errorTopic).delete()
    } catch {
      case t: Throwable => logger.error("error during deleting error topic " + t.getMessage)
    }

    try {
      aws.sqs.getQueueByName(config.resourceNames.outputQueue).foreach(_.delete())
    } catch {
      case t: Throwable => logger.error("error during deleting output queue " + t.getMessage)
    }

    try {
      aws.sns.createTopic(config.resourceNames.outputTopic).delete()
    } catch {
      case t: Throwable => logger.error("error during deleting output topic " + t.getMessage)
    }

    try {
      aws.sqs.getQueueByName(config.resourceNames.inputQueue).foreach(_.delete())
    } catch {
      case t: Throwable => logger.error("error during deleting input queue " + t.getMessage)
    }

    try {
      aws.sqs.getQueueByName(config.resourceNames.controlQueue).foreach(_.delete())
    } catch {
      case t: Throwable => logger.error("error during deleting control queue " + t.getMessage)
    }

    try {
      logger.info("delete manager group")
      aws.as.deleteAutoScalingGroup(config.managerAutoScalingGroup)
    } catch {
      case t: Throwable => logger.info("error during deleting manager group: " + t.getMessage)
    }

    logger.info("undeployed")
  }

}
