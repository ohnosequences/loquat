package ohnosequences.loquat

import utils._
import ohnosequences.statika._
import ohnosequences.datasets._
import ohnosequences.awstools._, sqs._, s3._

import com.typesafe.scalalogging.LazyLogging
import scala.util.Try
import collection.JavaConversions._


trait AnyLoquat { loquat =>

  type Config <: AnyLoquatConfig
  val  config: Config

  type DataProcessing <: AnyDataProcessingBundle
  val  dataProcessing: DataProcessing

  val dataMappings: List[DataMapping[DataProcessing]]

  lazy val fullName: String = this.getClass.getName.split("\\$").mkString(".")

  // Bundles hierarchy:
  case object worker extends WorkerBundle(dataProcessing, config)

  case object manager extends ManagerBundle(worker)(dataMappings) {
    override lazy val fullName: String = s"${loquat.fullName}.${this.toString}"
  }

  case object managerCompat extends CompatibleWithPrefix(fullName)(config.amiEnv, manager, config.metadata)

  final def check(user: LoquatUser): Unit = LoquatOps.check(config, user, dataMappings)
  final def deploy(user: LoquatUser): Unit = LoquatOps.deploy(config, user, dataMappings, managerCompat.userScript)
  final def undeploy(user: LoquatUser): Unit =
    LoquatOps.undeploy(
      config,
      AWSClients(user.localCredentials, config.region),
      TerminateManually
    )
}

abstract class Loquat[
  C <: AnyLoquatConfig,
  DP <: AnyDataProcessingBundle
](val config: C, val dataProcessing: DP
)(val dataMappings: List[DataMapping[DP]]) extends AnyLoquat {

  type Config = C
  type DataProcessing = DP
}



private[loquat]
case object LoquatOps extends LazyLogging {

  def checkInputData(aws: AWSClients, dataMappings: List[AnyDataMapping]): Seq[String] = {

    logger.info("Checking input S3 objects existence...")

    dataMappings flatMap { dataMapping =>

      // if an input object doesn't exist, we return an arror message
      dataMapping.remoteInput flatMap {
        case (dataKey, S3Resource(s3address)) => {
          // FIXME: should be in awstools
          val exists: Boolean = Try(
            aws.s3.listObjects(s3address.bucket, s3address.key).getObjectSummaries
          ).filter{ _.length > 0 }.isSuccess

          if (exists) print("+") else print("-")

          if (exists) None
          else Some(s"Input object [${dataKey.label}] doesn't exist at the address: [${s3address}]")
        }
        // if the mapping is not an S3Resource, we don't check
        case _ => None
      }
    }
  }

  // def checkDataMappings(aws: AWSClients, checkInputObjects: Boolean): Seq[String] = {
  // }

  def check(
    config: AnyLoquatConfig,
    user: LoquatUser,
    dataMappings: List[AnyDataMapping]
  ): Either[String, AWSClients] = {

    if (Try( user.localCredentials.getCredentials ).isFailure) {
      Left(s"Couldn't load local credentials: ${user.localCredentials}")
    } else {
      val aws = AWSClients(user.localCredentials, config.region)

      if(user.validateWithLogging(aws).nonEmpty) Left("User validation failed")
      else if (config.validateWithLogging(aws).nonEmpty) Left("Config validation failed")
      else {
        logger.info("Checking that data mappings define all the needed data keys...")

        val invalidDM = dataMappings.find { _.checkDataKeys.nonEmpty }

        invalidDM match {

          case Some(dm) => {
            dm.checkDataKeys foreach { msg => logger.error(msg) }
            Left("Some dataMappings are invalid")
          }

          case None => if (config.checkInputObjects) {
            val missingInputs = checkInputData(aws, dataMappings)
            missingInputs foreach { msg => logger.error(msg) }

            if (missingInputs.nonEmpty) Left("Some input data is missing")
            else Right(aws)
          } else Right(aws)
        }
      }
    }

  }


  def deploy(
    config: AnyLoquatConfig,
    user: LoquatUser,
    dataMappings: List[AnyDataMapping],
    managerUserScript: String
  ): Unit = {

    LoquatOps.check(config, user, dataMappings) match {
      case Left(msg) => logger.error(msg)
      case Right(aws) => {

        val names = config.resourceNames

        val managerGroup = config.managerConfig.autoScalingGroup(
          config.resourceNames.managerGroup,
          user.keypairName,
          config.iamRoleName
        )

        logger.info(s"Deploying loquat: ${config.loquatId}")


        Seq(
          Step( s"Creating input queue: ${names.inputQueue}" )(
            Try { aws.sqs.createQueue(names.inputQueue) }
          ),
          Step( s"Creating output queue: ${names.outputQueue}" )(
            Try { aws.sqs.createQueue(names.outputQueue) }
          ),
          Step( s"Creating error queue: ${names.errorQueue}" )(
            Try { aws.sqs.createQueue(names.errorQueue) }
          ),
          Step( s"Checking the bucket: ${names.bucket}" )(
            Try {
              if(aws.s3.doesBucketExist(names.bucket)) {
                logger.info(s"Bucket [${names.bucket}] already exists.")
              } else {
                logger.info(s"Bucket [${names.bucket}] doesn't exists. Trying to create it.")
                aws.s3.createBucket(names.bucket)
              }
            }
          ),
          Step( s"Creating notification topic: ${names.notificationTopic}" )(
            Try { aws.sns.createTopic(names.notificationTopic) }
              .map { topic =>
                if (!topic.isEmailSubscribed(user.email.toString)) {
                  logger.info(s"Subscribing [${user.email}] to the notification topic")
                  topic.subscribeEmail(user.email.toString)
                  logger.info("Check your email and confirm subscription")
                }
              }
          ),
          Step( s"Creating manager group: ${managerGroup.name}" )(
            Try { aws.as.fixAutoScalingGroupUserData(managerGroup, managerUserScript) }
              .map { asGroup =>
                aws.as.createAutoScalingGroup(asGroup)
                // TODO: make use of the managerGroup status tag
                utils.tagAutoScalingGroup(aws.as, asGroup, StatusTag.preparing)
              }
          ),
          Step("Loquat is running, now go to the amazon console and keep an eye on the progress")(
            util.Success(true)
          )
        ).foldLeft[Try[_]] {
          logger.info("Creating resources...")
            util.Success(true)
        } { (result: Try[_], next: Step[_]) =>
          result.flatMap(_ => next.execute)
        }

      }

    }

  }


  def undeploy(
    config: AnyLoquatConfig,
    aws: AWSClients,
    reason: AnyTerminationReason
  ): Unit = {
    logger.info(s"Undeploying loquat: ${config.loquatId}")

    val names = config.resourceNames

    Step("Sending notification on your email")(
      Try {
        val subject = "Loquat " + config.loquatId + " is terminated"
        val notificationTopic = aws.sns.createTopic(names.notificationTopic)
        notificationTopic.publish(reason.msg, subject)
      }
    ).execute

    Step(s"deleting workers group: ${names.workersGroup}")(
      Try { aws.as.deleteAutoScalingGroup(names.workersGroup) }
    ).execute

    Step(s"deleting error queue: ${names.errorQueue}")(
      Try { aws.sqs.getQueueByName(names.errorQueue).foreach(_.delete) }
    ).execute

    Step(s"deleting output queue: ${names.outputQueue}")(
      Try { aws.sqs.getQueueByName(names.outputQueue).foreach(_.delete) }
    ).execute

    Step(s"deleting input queue: ${names.inputQueue}")(
      Try { aws.sqs.getQueueByName(names.inputQueue).foreach(_.delete) }
    ).execute

    Step(s"deleting manager group: ${names.managerGroup}")(
      Try { aws.as.deleteAutoScalingGroup(names.managerGroup) }
    ).execute

    logger.info("Loquat is undeployed")
  }


  // These ops are useful for a running loquat. Use them from REPL (sbt console)
  // TODO: restore this code

  // def addDataMappings(loquat: AnyLoquat, dataMappings: List[AnyDataMapping]): Unit = {
  //
  //   val sqs = SQS.create(loquat.config.localCredentials)
  //   val inputQueue = sqs.get(loquat.config.resourceNames.inputQueue).get
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
