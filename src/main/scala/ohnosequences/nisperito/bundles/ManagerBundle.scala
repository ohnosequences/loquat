package ohnosequences.nisperito.bundles

import ohnosequences.nisperito._, pipas._

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._

import com.typesafe.scalalogging.LazyLogging

import ohnosequences.awstools.autoscaling.AutoScalingGroup
import ohnosequences.awstools.s3.ObjectAddress
import ohnosequences.awstools.AWSClients
import com.amazonaws.auth.InstanceProfileCredentialsProvider


// We don't want it to be used outside of this project
protected[nisperito]
trait AnyManagerBundle extends AnyBundle with LazyLogging { manager =>

  val fullName: String

  type Worker <: AnyWorkerBundle
  val  worker: Worker

  val config = worker.config

  case object workerCompat extends Compatible[Worker#Config#AMI, Worker](
    environment = config.ami,
    bundle = worker,
    metadata = config.metadata
  ) {
    override lazy val fullName: String = s"${manager.fullName}.${this.toString}"
  }

  val terminationDaemon = TerminationDaemonBundle(config)

  val bundleDependencies: List[AnyBundle] = List(terminationDaemon, LogUploaderBundle(config))

  lazy val aws: AWSClients = AWSClients.create(new InstanceProfileCredentialsProvider())

  def uploadInitialPipas(pipas: List[AnyPipa]) {
    try {
      logger.info("adding initial pipas to SQS")
      // FIXME: match on Option instead of get
      val inputQueue = aws.sqs.getQueueByName(config.resourceNames.inputQueue).get

      // logger.error(s"Couldn't access input queue: ${config.resourceNames.inputQueue}")

      // NOTE: we can send messages in parallel
      pipas.par.foreach { pipa =>
        inputQueue.sendMessage(upickle.default.write[SimplePipa](simplify(pipa)))
      }
      aws.s3.uploadString(config.pipasUploaded, "")
      logger.info("initial pipas are ready")
    } catch {
      case t: Throwable => logger.error("error during uploading initial pipas", t)
    }
  }


  def install: Results = {

    logger.info("manager is started")

    try {
      logger.info("checking if the initial pipas are uploaded")
      if (aws.s3.listObjects(config.pipasUploaded.bucket, config.pipasUploaded.key).isEmpty) {
        logger.warn("uploading initial pipas")
        uploadInitialPipas(config.pipas)
      } else {
        logger.warn("skipping uploading pipas")
      }
    } catch {
      case t: Throwable => logger.error("error during uploading initial pipas", t)
    }

    try {
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
      utils.tagAutoScalingGroup(aws.as, groupName, InstanceTags.INSTALLING.value)
    } catch {
      case t: Throwable => logger.error("error during creating workers autoscaling group", t)
    }

    try {
      logger.info("starting termination daemon")
      terminationDaemon.TerminationDaemonThread.start()
    } catch {
      case t: Throwable => logger.error("error during starting termination daemon", t)
    }

    success("manager installed")
    // } catch {
    //   case t: Throwable => {
    //     t.printStackTrace()
    //     aws.ec2.getCurrentInstance.foreach(_.terminate)
    //     failure("manager fails")
    //   }
    // }
  }
}

protected[nisperito]
abstract class ManagerBundle[W <: AnyWorkerBundle](val worker: W) extends AnyManagerBundle {

  type Worker = W
}
