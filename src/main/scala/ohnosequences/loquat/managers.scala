package ohnosequences.loquat

import dataMappings._, daemons._

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._

import com.typesafe.scalalogging.LazyLogging

import ohnosequences.awstools.autoscaling.AutoScalingGroup
import ohnosequences.awstools.s3.ObjectAddress
import ohnosequences.awstools.AWSClients
import com.amazonaws.auth.InstanceProfileCredentialsProvider
import scala.util.Try


// We don't want it to be used outside of this project
protected[loquat]
  trait AnyManagerBundle extends AnyBundle with LazyLogging { manager =>

    val fullName: String

    type Worker <: AnyWorkerBundle
    val  worker: Worker

    val config = worker.config

    case object workerCompat extends CompatibleWithPrefix(fullName)(
      environment = config.ami,
      bundle = worker,
      metadata = config.metadata
    ) {
      override lazy val fullName: String = s"${manager.fullName}.${this.toString}"
    }

    val terminationDaemon = TerminationDaemonBundle(config)

    val bundleDependencies: List[AnyBundle] = List(terminationDaemon, LogUploaderBundle(config))

    lazy val aws: AWSClients = AWSClients.create(new InstanceProfileCredentialsProvider())

    def uploadInitialDataMappings(dataMappings: List[AnyDataMapping]) {
      try {
        logger.info("adding initial dataMappings to SQS")
        // FIXME: match on Option instead of get
        val inputQueue = aws.sqs.getQueueByName(config.resourceNames.inputQueue).get

        // logger.error(s"Couldn't access input queue: ${config.resourceNames.inputQueue}")

        // NOTE: we can send messages in parallel
        dataMappings.par.foreach { dataMapping =>
          inputQueue.sendMessage(upickle.default.write[SimpleDataMapping](simplify(dataMapping)))
        }
        aws.s3.uploadString(config.dataMappingsUploaded, "")
        logger.info("initial dataMappings are ready")
      } catch {
        case t: Throwable => logger.error("error during uploading initial dataMappings", t)
      }
    }


    def instructions: AnyInstructions = {

      lazy val normalScenario: Instructions[Unit] = {
        Try {
          logger.info("manager is started")
          logger.info("checking if the initial dataMappings are uploaded")
          if (aws.s3.listObjects(config.dataMappingsUploaded.bucket, config.dataMappingsUploaded.key).isEmpty) {
            logger.warn("uploading initial dataMappings")
            uploadInitialDataMappings(config.dataMappings)
          } else {
            logger.warn("skipping uploading dataMappings")
          }
        } -&- {
          Try {
            aws.as.getAutoScalingGroupByName(config.resourceNames.managerGroup).get
          } map { group =>
            group.launchingConfiguration.instanceSpecs.keyName
          } map { keypairName =>

            val workersGroup = config.workersAutoScalingGroup(keypairName)

            logger.info("Setting up workers userScript")
            aws.as.fixAutoScalingGroupUserData(
              workersGroup,
              workerCompat.userScript
            )

            logger.info("Creating workers autoscaling group")
            aws.as.createAutoScalingGroup(workersGroup)

            logger.info("Creating tags for workers autoscaling group")
            utils.tagAutoScalingGroup(aws.as, config.resourceNames.workersGroup, utils.InstanceTags.INSTALLING.value)
          }
        } -&-
        Try {
          logger.info("starting termination daemon")
          terminationDaemon.TerminationDaemonThread.start()
        } -&-
        say("manager installed")
      }

      normalScenario -|- {
        Try {
          logger.error("Manager failed, trying to restart it")
          aws.ec2.getCurrentInstance.foreach(_.terminate)
        } ->-
        failure[Unit]("Manager failed during installation")
      }

    }
  }

protected[loquat]
  abstract class ManagerBundle[W <: AnyWorkerBundle](val worker: W)
    extends AnyManagerBundle { type Worker = W }
