package ohnosequences.nisperito

import ohnosequences.nisperito.bundles._, instructions._
import ohnosequences.nisperito.pipas._

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
  case object worker extends WorkerBundle(instructions, config)

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
    val names = config.resourceNames

    if(config.check == false)
      logger.error("something is wrong with the config")
    else {
      logger.info("the config seems to be fine")

      // FIXME: every action here should be checked before proceeding to the next one
      logger.info("creating resources...")

      logger.debug(s"creating error topic: ${names.errorTopic}")
      val errorTopic = aws.sns.createTopic(names.errorTopic)
      logger.debug(s"creating error queue: ${names.errorQueue}")
      val errorQueue = aws.sqs.createQueue(names.errorQueue)
      logger.debug("subscribing error queue to error topic")
      errorTopic.subscribeQueue(errorQueue)

      logger.debug(s"creating input queue: ${names.inputQueue}")
      val inputQueue = aws.sqs.createQueue(names.inputQueue)

      logger.debug(s"creating output topic: ${names.outputTopic}")
      val outputTopic = aws.sns.createTopic(names.outputTopic)
      logger.debug(s"creating output queue: ${names.outputQueue}")
      val outputQueue = aws.sqs.createQueue(names.outputQueue)
      logger.debug("subscribing output queue to output topic")
      outputTopic.subscribeQueue(outputQueue)

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

      logger.info("nisperito is running, now go to the amazon console and keep an eye on the progress")
    }
  }


  def undeploy(config: AnyNisperitoConfig): Unit = {
    logger.info(s"undeploying nisperito: ${config.nisperitoName} v${config.nisperitoVersion}")

    val aws = AWSClients.create(config.localCredentials)
    val names = config.resourceNames

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
      logger.info(s"deleting error topic: ${names.errorTopic}")
      aws.sns.createTopic(names.errorTopic).delete
    } catch {
      case t: Throwable => logger.error("error during deleting error topic", t)
    }

    try {
      logger.info(s"deleting output queue: ${names.outputQueue}")
      aws.sqs.getQueueByName(names.outputQueue).foreach(_.delete)
    } catch {
      case t: Throwable => logger.error("error during deleting output queue", t)
    }

    try {
      logger.info(s"deleting output topic: ${names.outputTopic}")
      aws.sns.createTopic(names.outputTopic).delete
    } catch {
      case t: Throwable => logger.error("error during deleting output topic", t)
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

    logger.info("nisperito is undeployed")
  }


  // These ops are useful for a running nisperito. Use them from REPL (sbt console)

  // def addPipas(nisperito: AnyNisperito, pipas: List[AnyPipa]): Unit = {
  //
  //   val sqs = SQS.create(nisperito.config.localCredentials)
  //   val inputQueue = sqs.getQueueByName(nisperito.config.resourceNames.inputQueue).get
  //   pipas.foreach {
  //     t => inputQueue.sendMessage(upickle.default.write[SimplePipa](t))
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