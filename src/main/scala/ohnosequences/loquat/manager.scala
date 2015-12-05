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

  // FIXME: rewrite this method
  def uploadInitialDataMappings(dataMappings: List[AnyDataMapping]): Unit = {
    try {
      logger.info("adding initial dataMappings to SQS")
      // FIXME: match on Option instead of get
      val inputQueue = aws.sqs.getQueueByName(config.resourceNames.inputQueue).get

      // logger.error(s"Couldn't access input queue: ${config.resourceNames.inputQueue}")

      // NOTE: we can send messages in parallel
      dataMappings.par.foreach { dataMapping =>
        inputQueue.sendMessage(upickle.default.write[SimpleDataMapping](dataMapping.simplify))
      }
      aws.s3.uploadString(config.dataMappingsUploaded, "")
      logger.info("initial dataMappings are ready")
    } catch {
      case t: Throwable => logger.error("error during uploading initial dataMappings", t)
    }
  }


  def instructions: AnyInstructions = {

    lazy val normalScenario: Instructions[Unit] = {
      LazyTry {
        logger.info("manager is started")
        logger.info("checking if the initial dataMappings are uploaded")
        if (aws.s3.listObjects(config.dataMappingsUploaded.bucket, config.dataMappingsUploaded.key).isEmpty) {
          logger.warn("uploading initial dataMappings")
          uploadInitialDataMappings(config.dataMappings)
        } else {
          logger.warn("skipping uploading dataMappings")
        }
      } -&-
      LazyTry {
        aws.as.getAutoScalingGroupByName(config.resourceNames.managerGroup) map { group =>
          group.launchConfiguration.launchSpecs.keyName
        } map { keypairName =>

          logger.info("Setting up workers userScript")
          val workersGroup = aws.as.fixAutoScalingGroupUserData(
            config.workersConfig.autoScalingGroup(
              config.resourceNames.workersGroup,
              keypairName,
              config.iamRoleName
            ),
            workerCompat.userScript
          )

          logger.info("Creating workers autoscaling group")
          aws.as.createAutoScalingGroup(workersGroup)

          logger.info("Waiting for the workers autoscaling group creation")
          utils.waitForResource(
            getResource = aws.as.getAutoScalingGroupByName(workersGroup.name),
            tries = 30,
            timeStep = 5.seconds
          )

          logger.info("Creating tags for workers autoscaling group")
          utils.tagAutoScalingGroup(aws.as, workersGroup.name, utils.InstanceTags.INSTALLING.value)
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

    // FIXME: this should happen only if the normal scenario fails!
    normalScenario -|- failScenario
  }
}

private[loquat]
abstract class ManagerBundle[W <: AnyWorkerBundle](val worker: W)
  extends AnyManagerBundle { type Worker = W }
