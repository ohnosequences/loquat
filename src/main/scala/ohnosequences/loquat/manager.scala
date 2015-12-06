package ohnosequences.loquat

import utils._

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._

import com.typesafe.scalalogging.LazyLogging

import ohnosequences.awstools.autoscaling.AutoScalingGroup
import ohnosequences.awstools.AWSClients
import com.amazonaws.auth.InstanceProfileCredentialsProvider

import scala.concurrent.duration._
import scala.util.Try


// We don't want it to be used outside of this project
private[loquat]
trait AnyManagerBundle extends AnyBundle with LazyLogging { manager =>

  val fullName: String

  type Worker <: AnyWorkerBundle
  val  worker: Worker

  val config = worker.config

  case object workerCompat extends CompatibleWithPrefix(fullName)(
    environment = config.amiEnv,
    bundle = worker,
    metadata = config.metadata
  ) {
    override lazy val fullName: String = s"${manager.fullName}.${this.toString}"
  }

  lazy final val scheduler = Scheduler(2)

  val bundleDependencies: List[AnyBundle] = List(
    LogUploaderBundle(config, scheduler),
    TerminationDaemonBundle(config, scheduler)
  )

  lazy val aws: AWSClients = AWSClients.create(new InstanceProfileCredentialsProvider())

  def uploadInitialDataMappings(dataMappings: List[AnyDataMapping]): Try[Unit] = {
    val managerStatus = aws.as.getTagValue(config.resourceNames.managerGroup, StatusTag.label)

    if (managerStatus == Some(StatusTag.running.status)) {

      logger.info("DataMappings are supposed to be in the queue already")
      scala.util.Success( () )
    } else {

      lazy val getQueue = Try { aws.sqs.getQueueByName(config.resourceNames.inputQueue).get }

      lazy val upload = getQueue match {
        case scala.util.Failure(t) => {
          logger.error(s"Couldn't access input queue: ${config.resourceNames.inputQueue}")
          scala.util.Failure[Unit](t)
        }
        case scala.util.Success(inputQueue) => Try {
          logger.debug("Adding initial dataMappings to SQS")

          // NOTE: we can send messages in parallel
          dataMappings.par.foreach { dataMapping =>
            inputQueue.sendMessage(upickle.default.write[SimpleDataMapping](dataMapping.simplify))
          }
        }
      }

      upload match {
        case fail @ scala.util.Failure(_) => {
          logger.error("Failed to upload initial dataMappings")
          fail
        }
        case scala.util.Success(inputQueue) => Try {
          // NOTE: we tag manager group as running
          aws.as.createTags(config.resourceNames.managerGroup, StatusTag.running)
          logger.info("Initial dataMappings are ready")
        }
      }
    }
  }


  def instructions: AnyInstructions = {

    lazy val normalScenario: Instructions[Unit] = {
      LazyTry {
        logger.debug("Uploading initial dataMappings to the input queue")
        uploadInitialDataMappings(config.dataMappings).get
      } -&-
      LazyTry {
        aws.as.getAutoScalingGroupByName(config.resourceNames.managerGroup) map { group =>
          group.launchConfiguration.launchSpecs.keyName
        } map { keypairName =>

          logger.debug("Setting up workers userScript")
          val workersGroup = aws.as.fixAutoScalingGroupUserData(
            config.workersConfig.autoScalingGroup(
              config.resourceNames.workersGroup,
              keypairName,
              config.iamRoleName
            ),
            workerCompat.userScript
          )

          logger.debug("Creating workers autoscaling group")
          aws.as.createAutoScalingGroup(workersGroup)

          logger.debug("Waiting for the workers autoscaling group creation")
          utils.waitForResource(
            getResource = aws.as.getAutoScalingGroupByName(workersGroup.name),
            tries = 30,
            timeStep = 5.seconds
          )

          logger.debug("Creating tags for workers autoscaling group")
          utils.tagAutoScalingGroup(aws.as, workersGroup, StatusTag.running)
        }
      } -&-
      say("manager installed")
    }

    lazy val failScenario = {
      LazyTry {
        logger.error("Manager failed, trying to restart it")
        aws.ec2.getCurrentInstance.foreach(_.terminate)
      } -&-
      failure[Unit]("Manager failed during installation")
    }

    normalScenario -|- failScenario
  }
}

private[loquat]
abstract class ManagerBundle[W <: AnyWorkerBundle](val worker: W)
  extends AnyManagerBundle { type Worker = W }
