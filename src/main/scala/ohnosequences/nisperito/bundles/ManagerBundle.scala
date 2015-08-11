package ohnosequences.nisperito.bundles

import ohnosequences.nisperito._, tasks._

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

  def uploadInitialTasks(tasks: List[AnyTask]) {
    try {

      logger.info("adding initial tasks to SQS")
      val inputQueue = aws.sqs.createQueue(config.resourceNames.inputQueue)

      // NOTE: we can send messages in parallel
      tasks.par.foreach { task =>
        inputQueue.sendMessage(upickle.default.write[SimpleTask](task))
      }
      aws.s3.putWholeObject(config.tasksUploaded, "")
      logger.info("initial tasks are ready")
    } catch {
      case t: Throwable => logger.error("error during uploading initial tasks", t)
    }
  }


  def install: Results = {

    logger.info("manager is started")
    try {

      if (aws.s3.listObjects(config.tasksUploaded.bucket, config.tasksUploaded.key).isEmpty) {
        uploadInitialTasks(config.tasks)
      } else {
        logger.warn("skipping uploading tasks")
      }

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

      logger.info("starting termination daemon")
      terminationDaemon.TerminationDaemonThread.start()

      success("manager installed")
    } catch {
      case t: Throwable => {
        t.printStackTrace()
        aws.ec2.getCurrentInstance.foreach(_.terminate)
        failure("manager fails")
      }
    }
  }
}

protected[nisperito]
abstract class ManagerBundle[W <: AnyWorkerBundle](val worker: W) extends AnyManagerBundle {

  type Worker = W
}
