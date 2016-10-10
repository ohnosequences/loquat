package ohnosequences.loquat

import utils._

import ohnosequences.statika._

import com.typesafe.scalalogging.LazyLogging

import ohnosequences.awstools.sqs._
import ohnosequences.awstools.autoscaling.AutoScalingGroup

import java.util.concurrent.Executors
import scala.concurrent._, duration._
import scala.util.Try


// We don't want it to be used outside of this project
private[loquat]
trait AnyManagerBundle extends AnyBundle with LazyLogging { manager =>

  val fullName: String

  type Worker <: AnyWorkerBundle
  val  worker: Worker

  val config = worker.config

  val dataMappings: List[DataMapping[Worker#DataProcessingBundle]]

  case object workerCompat extends CompatibleWithPrefix(fullName)(
    environment = config.amiEnv,
    bundle = worker,
    metadata = config.metadata
  ) {
    override lazy val fullName: String = s"${manager.fullName}.${this.toString}"
  }

  lazy final val scheduler = Scheduler(2)

  lazy val loggerBundle = LogUploaderBundle(config, scheduler)
  lazy val terminationBundle = TerminationDaemonBundle(config, scheduler, dataMappings.length)

  val bundleDependencies: List[AnyBundle] = List(
    loggerBundle,
    terminationBundle
  )

  lazy val aws = instanceAWSClients(config)

  def uploadInitialDataMappings: Try[Unit] = {
    val managerStatus = aws.as.getTagValue(config.resourceNames.managerGroup, StatusTag.label)

    if (managerStatus == Some(StatusTag.running.status)) {

      logger.info("DataMappings are supposed to be in the queue already")
      scala.util.Success( () )
    } else {

      val queue: Try[Queue] = aws.sqs.get(config.resourceNames.inputQueue)
        .recoverWith { case t =>
          logger.error(s"Couldn't access input queue: ${config.resourceNames.inputQueue}")
          scala.util.Failure[Queue](t)
        }

      val msgs: Iterator[String] = dataMappings.toIterator.zipWithIndex.map { case (dataMapping, ix) =>
        upickle.default.write[SimpleDataMapping](
          SimpleDataMapping(
            id = ix.toString,
            inputs = toMap(dataMapping.remoteInput),
            outputs = toMap(dataMapping.remoteOutput)
          )
        )
      }

      // TODO: 100 connections? more?
      val executor = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(100))

      val tryToSend: Try[SendBatchResult] = queue.map { inputQueue =>
        logger.debug("Adding initial dataMappings to SQS")

        Await.result(inputQueue.sendBatch(msgs)(executor), 1.hour)
      }

      executor.shutdown()

      tryToSend.flatMap { result =>
        if (result.failures.nonEmpty) {
          // Probably printing all errors is too much, this is just to see which kinds of errors occure
          logger.error("Failed to batch send initial dataMappings:")
          result.failures.take(10).foreach { case (msg, err) =>
            logger.error(s"Error ${err.getCode}: ${err.getMessage} (sender fault: ${err.getSenderFault}). \n${msg}\n")
          }
          // TODO: better exception here?
          scala.util.Failure(new RuntimeException("Failed to upload initial dataMappings"))
        } else {
          // NOTE: we tag manager group as running
          aws.as.createTags(config.resourceNames.managerGroup, StatusTag.running)
          logger.info("Initial dataMappings are ready")
          scala.util.Success( () )
        }
      }

    }
  }


  def instructions: AnyInstructions = {

    lazy val normalScenario: Instructions[Unit] = {
      LazyTry {
        logger.debug("Uploading initial dataMappings to the input queue")
        uploadInitialDataMappings.get
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

        val subject = s"Loquat ${config.loquatId} manager failed during installation"
        val logTail = loggerBundle.logFile.lines.toSeq.takeRight(20).mkString("\n") // 20 last lines
        val message = s"""${subject}. It will try to restart. If it's a fatal failure, you should manually undeploy the loquat.
          |Full log is at [${loggerBundle.logS3.getOrElse("Failed to get log S3 location")}]
          |Here is its tail:
          |
          |[...]
          |${logTail}
          |""".stripMargin

        val notificationTopic = aws.sns.createTopic(config.resourceNames.notificationTopic)
        notificationTopic.publish(message, subject)

        aws.ec2.getCurrentInstance.foreach(_.terminate)
      } -&-
      failure[Unit]("Manager failed during installation")
    }

    normalScenario -|- failScenario
  }
}

private[loquat]
abstract class ManagerBundle[W <: AnyWorkerBundle](val worker: W)
  (val dataMappings: List[DataMapping[W#DataProcessingBundle]])
  extends AnyManagerBundle { type Worker = W }
