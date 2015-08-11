package ohnosequences.nisperito

import ohnosequences.nisperito.bundles._, instructions._
import ohnosequences.nisperito.tasks._

import ohnosequences.statika.bundles._

import ohnosequences.awstools.AWSClients
import ohnosequences.awstools.sqs._
import ohnosequences.awstools.autoscaling._

import com.amazonaws.services.autoscaling.model._
import com.typesafe.scalalogging.LazyLogging


trait AnyNisperito { nisperito =>

  type Config <: AnyNisperitoConfig
  val  config: Config

  type Instructions <: AnyInstructionsBundle
  val  instructions: Instructions

  lazy val fullName: String = this.getClass.getName.split("\\$").mkString(".")

  // Bundles hierarchy:
  case object resources extends ResourcesBundle(config)

  case object worker extends WorkerBundle(instructions, resources)
  case object manager extends ManagerBundle(worker) {
    override lazy val fullName: String = s"${nisperito.fullName}.${this.toString}"
  }

  case object managerCompat extends Compatible(config.ami, manager, config.metadata) {
    override lazy val fullName: String = s"${nisperito.fullName}.${this.toString}"
  }

  final def deploy(): Unit = NisperitoOps.deploy(config, managerCompat.userScript)
  final def undeploy(): Unit = NisperitoOps.undeploy(config)
}

abstract class Nisperito[
  C <: AnyNisperitoConfig,
  I <: AnyInstructionsBundle
](val config: C, val instructions: I) extends AnyNisperito {

  type Config = C
  type Instructions = I
}



object NisperitoOps extends LazyLogging {

  def deploy(
    config: AnyNisperitoConfig,
    managerUserScript: String
  ): Unit = {
    logger.info(s"deploying nisperito: ${config.nisperitoName} v${config.nisperitoVersion}")

    val aws = AWSClients.create(config.localCredentials)

    if(config.check == false)
      logger.error("something is wrong with the config")
    else {
      logger.info("the config seems to be fine")

      // FIXME: every action here should be checked before proceeding to the next one

      logger.info(s"creating temporary bucket: ${config.resourceNames.bucket}")
      aws.s3.createBucket(config.resourceNames.bucket)

      logger.info(s"creating notification topic: ${config.notificationTopic}")
      val topic = aws.sns.createTopic(config.notificationTopic)

      if (!topic.isEmailSubscribed(config.email)) {
        logger.info(s"subscribing [${config.email}] to the notification topic")
        topic.subscribeEmail(config.email)
        logger.info("check your email and confirm subscription")
      }

      logger.info(s"creating manager group: ${config.managerAutoScalingGroup.name}")
      val managerGroup = aws.as.fixAutoScalingGroupUserData(config.managerAutoScalingGroup, managerUserScript)
      aws.as.createAutoScalingGroup(managerGroup)

      logger.info("creating tags for the manager autoscaling group")
      utils.tagAutoScalingGroup(aws.as, managerGroup.name, "manager")

      logger.info("nisperito is running, now go to the amazon console and keep an eye on the progress")
    }
  }


  def undeploy(config: AnyNisperitoConfig): Unit = {
    logger.info(s"undeploying nisperito: ${config.nisperitoName} v${config.nisperitoVersion}")

    val aws = AWSClients.create(config.localCredentials)

    logger.info("sending notification on your email")
    try {
      val subject = "Nisperito " + config.nisperitoId + " terminated"
      val notificationTopic = aws.sns.createTopic(config.notificationTopic)
      notificationTopic.publish("manual termination", subject)
    } catch {
      case t: Throwable => logger.error("error during sending notification", t)
    }

    try {
      logger.info(s"deleting workers group: ${config.workersAutoScalingGroup.name}")
      aws.as.deleteAutoScalingGroup(config.workersAutoScalingGroup)
    } catch {
      case t: Throwable => logger.error("error during deleting workers group", t)
    }

    try {
      logger.info(s"deleting temporary bucket: ${config.resourceNames.bucket}")
      aws.s3.deleteBucket(config.resourceNames.bucket)
    } catch {
      case t: Throwable => logger.error("error during deleting temporary bucket", t)
    }

    try {
      logger.info(s"deleting error queue: ${config.resourceNames.errorQueue}")
      aws.sqs.getQueueByName(config.resourceNames.errorQueue).foreach(_.delete)
    } catch {
      case t: Throwable => logger.error("error during deleting error queue", t)
    }

    try {
      logger.info(s"deleting error topic: ${config.resourceNames.errorTopic}")
      aws.sns.createTopic(config.resourceNames.errorTopic).delete
    } catch {
      case t: Throwable => logger.error("error during deleting error topic", t)
    }

    try {
      logger.info(s"deleting output queue: ${config.resourceNames.outputQueue}")
      aws.sqs.getQueueByName(config.resourceNames.outputQueue).foreach(_.delete)
    } catch {
      case t: Throwable => logger.error("error during deleting output queue", t)
    }

    try {
      logger.info(s"deleting output topic: ${config.resourceNames.outputTopic}")
      aws.sns.createTopic(config.resourceNames.outputTopic).delete
    } catch {
      case t: Throwable => logger.error("error during deleting output topic", t)
    }

    try {
      logger.info(s"deleting input queue: ${config.resourceNames.inputQueue}")
      aws.sqs.getQueueByName(config.resourceNames.inputQueue).foreach(_.delete)
    } catch {
      case t: Throwable => logger.error("error during deleting input queue", t)
    }

    try {
      logger.info(s"deleting manager group: ${config.managerAutoScalingGroup.name}")
      aws.as.deleteAutoScalingGroup(config.managerAutoScalingGroup)
    } catch {
      case t: Throwable => logger.error("error during deleting manager group", t)
    }

    logger.info("nisperito is undeployed")
  }


  // These ops are useful for a running nisperito. Use them from REPL (sbt console)

  // def addTasks(nisperito: AnyNisperito, tasks: List[AnyTask]): Unit = {
  //
  //   val sqs = SQS.create(nisperito.config.localCredentials)
  //   val inputQueue = sqs.getQueueByName(nisperito.config.resourceNames.inputQueue).get
  //   tasks.foreach {
  //     t => inputQueue.sendMessage(upickle.default.write[SimpleTask](t))
  //   }
  // }
  //
  // def updateWorkersGroupSize(nisperito: AnyNisperito, groupSize: WorkersGroupSize): Unit = {
  //
  //   val asClient = AutoScaling.create(nisperito.config.localCredentials, nisperito.resources.aws.ec2).as
  //   asClient.updateAutoScalingGroup(new UpdateAutoScalingGroupRequest()
  //     .withAutoScalingGroupName(nisperito.config.workersAutoScalingGroup.name)
  //     .withMinSize(groupSize.min)
  //     .withDesiredCapacity(groupSize.desired)
  //     .withMaxSize(groupSize.max)
  //   )
  // }
}
