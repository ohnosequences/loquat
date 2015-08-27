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

      Try {
        logger.info("manager is started")
        logger.info("checking if the initial dataMappings are uploaded")
        if (aws.s3.listObjects(config.dataMappingsUploaded.bucket, config.dataMappingsUploaded.key).isEmpty) {
          logger.warn("uploading initial dataMappings")
          uploadInitialDataMappings(config.dataMappings)
        } else {
          logger.warn("skipping uploading dataMappings")
        }
      } -&- { Try {
        logger.info("generating workers userScript")
        val workersGroup = aws.as.fixAutoScalingGroupUserData(
          config.workersAutoScalingGroup,
          workerCompat.userScript
        )

        logger.info("running workers auto scaling group")
        aws.as.createAutoScalingGroup(workersGroup)

        val groupName = config.workersAutoScalingGroup.name

        utils.waitForResource[AutoScalingGroup] {
          println("waiting for manager autoscalling")
          aws.as.getAutoScalingGroupByName(groupName)
        }

        logger.info("creating tags")
        utils.tagAutoScalingGroup(aws.as, groupName, utils.InstanceTags.INSTALLING.value)
      } recover {
        case t: Throwable => logger.error("error during creating workers autoscaling group", t)
      }} -&- { Try {
        logger.info("starting termination daemon")
        terminationDaemon.TerminationDaemonThread.start()
      } recover {
        case t: Throwable => logger.error("error during starting termination daemon", t)
      }} -&- say("manager installed")

      // FIXME: catch fatal exceptions and relaunch manager instance
      // } catch {
      //   case t: Throwable => {
      //     t.printStackTrace()
      //     aws.ec2.getCurrentInstance.foreach(_.terminate)
      //     failure("manager fails")
      //   }
      // }
    }
  }

protected[loquat]
  abstract class ManagerBundle[W <: AnyWorkerBundle](val worker: W)
    extends AnyManagerBundle { type Worker = W }
