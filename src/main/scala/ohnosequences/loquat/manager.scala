package ohnosequences.loquat

import utils._

import ohnosequences.statika._

import com.typesafe.scalalogging.LazyLogging

import com.amazonaws.PredefinedClientConfigurations
import com.amazonaws.auth.InstanceProfileCredentialsProvider
import ohnosequences.awstools._, sqs._, sns._, ec2._, autoscaling._

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

  lazy val names = config.resourceNames

  def uploadInitialDataMappings: Try[Unit] = {

    val sqs = SQSClient(
      config.region,
      InstanceProfileCredentialsProvider.getInstance(),
      // TODO: 100 connections? more?
      PredefinedClientConfigurations.defaultConfig.withMaxConnections(100)
    )

    val queue: Try[Queue] = sqs.get(names.inputQueue)
      .recoverWith { case t =>
        logger.error(s"Couldn't access input queue: ${names.inputQueue}")
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


    // Sending initial datamappings in parallel
    val executor = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(30))

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
        aws.as.setTags(
          names.managerGroup,
          Map(StatusTag.label -> StatusTag.running.status)
        )
        logger.info("Initial dataMappings are ready")
        scala.util.Success( () )
      }
    }

  }


  def instructions: AnyInstructions = {

    lazy val normalScenario: Instructions[Unit] = {
      LazyTry {
        logger.debug("Uploading initial dataMappings to the input queue")

        aws.as.tagValue(
          names.managerGroup,
          StatusTag.label
        ) match {

          case scala.util.Success(StatusTag.running.status) => {
            logger.info("DataMappings are supposed to be in the queue already")
            // scala.util.Success( () )
          }

          case _ => uploadInitialDataMappings
        }
      } -&-
      LazyTry {
        logger.debug("Creating workers launch configuration")
        aws.as.getLaunchConfig(names.managerLaunchConfig) map { launchConfig =>

          aws.as.createLaunchConfig(
            names.workersLaunchConfig,
            config.workersConfig.purchaseModel,
            LaunchSpecs(config.workersConfig.instanceSpecs)(
              keyName = launchConfig.getKeyName,
              userData = workerCompat.userScript,
              instanceProfile = Some(config.iamRoleName),
              deviceMapping = config.workersConfig.deviceMapping
            )
          )
        }
      } -&-
      LazyTry {
        logger.debug("Creating workers autoscaling group")
        aws.as.createGroup(
          names.workersGroup,
          names.workersLaunchConfig,
          config.workersConfig.groupSize,
          config.workersConfig.availabilityZones
        )
      } -&-
      LazyTry {
        logger.debug("Creating tags for workers autoscaling group")
        aws.as.setTags(names.workersGroup, Map(
          "product" -> "loquat",
          "group"   -> names.workersGroup,
          StatusTag.label -> StatusTag.running.status
        ))
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

        aws.sns
          .getOrCreate(names.notificationTopic)
          .map { _.publish(message, subject) }

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
