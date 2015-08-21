package ohnosequences.loquat

import dataMappings._, instructions._

import ohnosequences.statika.bundles._

import ohnosequences.awstools.AWSClients
import ohnosequences.awstools.sqs._
import ohnosequences.awstools.autoscaling._

import com.amazonaws.services.autoscaling.model._
import com.typesafe.scalalogging.LazyLogging


trait AnyLoquat { loquat =>

  type Config <: AnyLoquatConfig
  val  config: Config

  type Instructions <: AnyInstructionsBundle
  val  instructions: Instructions

  lazy val fullName: String = this.getClass.getName.split("\\$").mkString(".")

  // Bundles hierarchy:
  case object worker extends WorkerBundle(instructions, config)

  case object manager extends ManagerBundle(worker) {
    override lazy val fullName: String = s"${loquat.fullName}.${this.toString}"
  }

  case object managerCompat extends Compatible(config.ami, manager, config.metadata) {
    override lazy val fullName: String = s"${loquat.fullName}.${this.toString}"
  }

  final def deploy(): Unit = LoquatOps.deploy(config, managerCompat.userScript)
  final def undeploy(): Unit = LoquatOps.undeploy(config)
}

abstract class Loquat[
  C <: AnyLoquatConfig,
  I <: AnyInstructionsBundle
](val config: C, val instructions: I) extends AnyLoquat {

  type Config = C
  type Instructions = I
}



object LoquatOps extends LazyLogging {

  def deploy(
    config: AnyLoquatConfig,
    managerUserScript: String
  ): Unit = {
    logger.info(s"deploying loquat: ${config.loquatName} v${config.loquatVersion}")

    val aws = AWSClients.create(config.localCredentials)
    val names = config.resourceNames

    if(config.check == false)
      logger.error("something is wrong with the config")
    else {
      logger.info("the config seems to be fine")

      // FIXME: every action here should be checked before proceeding to the next one
      logger.info("creating resources...")

      logger.debug(s"creating input queue: ${names.inputQueue}")
      val inputQueue = aws.sqs.createQueue(names.inputQueue)

      logger.debug(s"creating output queue: ${names.outputQueue}")
      val outputQueue = aws.sqs.createQueue(names.outputQueue)

      logger.debug(s"creating error queue: ${names.errorQueue}")
      val errorQueue = aws.sqs.createQueue(names.errorQueue)

      logger.debug(s"creating temporary bucket: ${names.bucket}")
      aws.s3.createBucket(names.bucket)

      logger.debug(s"creating notification topic: ${config.notificationTopic}")
      val topic = aws.sns.createTopic(config.notificationTopic)

      if (!topic.isEmailSubscribed(config.email)) {
        logger.info(s"subscribing [${config.email}] to the notification topic")
        topic.subscribeEmail(config.email)
        logger.info("check your email and confirm subscription")
      }

      logger.debug(s"creating manager group: ${config.managerAutoScalingGroup.name}")
      val managerGroup = aws.as.fixAutoScalingGroupUserData(config.managerAutoScalingGroup, managerUserScript)
      aws.as.createAutoScalingGroup(managerGroup)

      logger.debug("creating tags for the manager autoscaling group")
      utils.tagAutoScalingGroup(aws.as, managerGroup.name, "manager")

      logger.info("loquat is running, now go to the amazon console and keep an eye on the progress")
    }
  }


  def undeploy(config: AnyLoquatConfig): Unit = {
    logger.info(s"undeploying loquat: ${config.loquatName} v${config.loquatVersion}")

    val aws = AWSClients.create(config.localCredentials)
    val names = config.resourceNames

    logger.info("sending notification on your email")
    try {
      val subject = "Loquat " + config.loquatId + " terminated"
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
      logger.info(s"deleting temporary bucket: ${names.bucket}")
      aws.s3.deleteBucket(names.bucket)
    } catch {
      case t: Throwable => logger.error("error during deleting temporary bucket", t)
    }

    try {
      logger.info(s"deleting error queue: ${names.errorQueue}")
      aws.sqs.getQueueByName(names.errorQueue).foreach(_.delete)
    } catch {
      case t: Throwable => logger.error("error during deleting error queue", t)
    }

    try {
      logger.info(s"deleting output queue: ${names.outputQueue}")
      aws.sqs.getQueueByName(names.outputQueue).foreach(_.delete)
    } catch {
      case t: Throwable => logger.error("error during deleting output queue", t)
    }

    try {
      logger.info(s"deleting input queue: ${names.inputQueue}")
      aws.sqs.getQueueByName(names.inputQueue).foreach(_.delete)
    } catch {
      case t: Throwable => logger.error("error during deleting input queue", t)
    }

    try {
      logger.info(s"deleting manager group: ${config.managerAutoScalingGroup.name}")
      aws.as.deleteAutoScalingGroup(config.managerAutoScalingGroup)
    } catch {
      case t: Throwable => logger.error("error during deleting manager group", t)
    }

    logger.info("loquat is undeployed")
  }


  // These ops are useful for a running loquat. Use them from REPL (sbt console)

  // def addDataMappings(loquat: AnyLoquat, dataMappings: List[AnyDataMapping]): Unit = {
  //
  //   val sqs = SQS.create(loquat.config.localCredentials)
  //   val inputQueue = sqs.getQueueByName(loquat.config.resourceNames.inputQueue).get
  //   dataMappings.foreach {
  //     t => inputQueue.sendMessage(upickle.default.write[SimpleDataMapping](t))
  //   }
  // }
  //
  // def updateWorkersGroupSize(loquat: AnyLoquat, groupSize: WorkersGroupSize): Unit = {
  //
  //   val asClient = AutoScaling.create(loquat.config.localCredentials, loquat.resources.aws.ec2).as
  //   asClient.updateAutoScalingGroup(new UpdateAutoScalingGroupRequest()
  //     .withAutoScalingGroupName(loquat.config.workersAutoScalingGroup.name)
  //     .withMinSize(groupSize.min)
  //     .withDesiredCapacity(groupSize.desired)
  //     .withMaxSize(groupSize.max)
  //   )
  // }
}
