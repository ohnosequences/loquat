package ohnosequences.nisperito

import ohnosequences.nisperito.bundles._
import ohnosequences.nisperito.tasks._

import ohnosequences.statika.bundles._

import ohnosequences.awstools.AWSClients
import ohnosequences.awstools.sqs._
import ohnosequences.awstools.autoscaling._

import com.amazonaws.services.autoscaling.model._
import org.clapper.avsl.Logger


trait AnyNisperito {

  type Config <: AnyNisperitoConfig
  val  config: Config

  type Instructions <: AnyInstructionsBundle
  val  instructions: Instructions

  // Bundles hierarchy:
  case object resources extends ResourcesBundle(config)

  case object worker extends WorkerBundle(instructions, resources)
  case object manager extends ManagerBundle(worker)

  case object managerCompat extends Compatible(config.ami, manager, config.metadata)

  def main(args: Array[String]): Unit = args.toList match {
    case List("deploy") => NisperitoOps.deploy(config, managerCompat.userScript)
    case List("undeploy") => NisperitoOps.undeploy(config)
    case _ => println("Wrong command. Should be either 'deploy' or 'undeploy' without arguments.")
  }
}

abstract class Nisperito[
  C <: AnyNisperitoConfig,
  I <: AnyInstructionsBundle
](val config: C, val instructions: I) {

  type Config = C
  type Instructions = I
}



object NisperitoOps {

  def deploy(
    config: AnyNisperitoConfig,
    managerUserScript: String
  ): Unit = {

    val logger = Logger(this.getClass)
    val aws = AWSClients.create(config.localCredentials)

    if(config.check) {
      return
    }

    aws.s3.createBucket(config.resourceNames.bucket)

    logger.info("creating notification topic: " + config.notificationTopic)

    val topic = aws.sns.createTopic(config.notificationTopic)

    if (!topic.isEmailSubscribed(config.email)) {
      logger.info("subscribing " + config.email + " to notification topic")
      topic.subscribeEmail(config.email)
      logger.info("please confirm subscription")
    }

    logger.info("running manager auto scaling group")
    // val managerUserScript = managerCompat.userScript
    val managerGroup = aws.as.fixAutoScalingGroupUserData(config.managerAutoScalingGroup, managerUserScript)
    aws.as.createAutoScalingGroup(managerGroup)

    logger.info("creating tags")
    utils.tagAutoScalingGroup(aws.as, managerGroup.name, "manager")
  }


  def undeploy(config: AnyNisperitoConfig): Unit = {

    val logger = Logger(this.getClass)
    val aws = AWSClients.create(config.localCredentials)

    logger.info("send notification")
    try {
      val subject = "Nisperito " + config.nisperitoId + " terminated"
      val notificationTopic = aws.sns.createTopic(config.notificationTopic)
      notificationTopic.publish("manual termination", subject)
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
      logger.info("delete manager group")
      aws.as.deleteAutoScalingGroup(config.managerAutoScalingGroup)
    } catch {
      case t: Throwable => logger.info("error during deleting manager group: " + t.getMessage)
    }

    logger.info("undeployed")
  }


  // These ops are useful for a running nisperito. Use them from REPL (sbt console)

  def addTasks(nisperito: AnyNisperito, tasks: List[AnyTask]): Unit = {

    val sqs = SQS.create(nisperito.config.localCredentials)
    val inputQueue = sqs.getQueueByName(nisperito.config.resourceNames.inputQueue).get
    tasks.foreach {
      case tt@TinyTask(_, _, _) => inputQueue.sendMessage(upickle.default.write[TinyTask](tt))
      case bt@BigTask(_, _, _) => inputQueue.sendMessage(upickle.default.write[BigTask](bt))
    }
  }

  def updateWorkersGroupSize(nisperito: AnyNisperito, groupSize: WorkersGroupSize): Unit = {

    val asClient = AutoScaling.create(nisperito.config.localCredentials, nisperito.resources.aws.ec2).as
    asClient.updateAutoScalingGroup(new UpdateAutoScalingGroupRequest()
      .withAutoScalingGroupName(nisperito.config.workersAutoScalingGroup.name)
      .withMinSize(groupSize.min)
      .withDesiredCapacity(groupSize.desired)
      .withMaxSize(groupSize.max)
    )
  }
}
