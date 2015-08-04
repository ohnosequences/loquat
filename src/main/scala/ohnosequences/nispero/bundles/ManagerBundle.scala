package ohnosequences.nispero.bundles

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._
import ohnosequences.statika.aws._

import ohnosequences.nispero.{TasksProvider, InstanceTags}
import org.clapper.avsl.Logger

import ohnosequences.awstools.autoscaling.AutoScalingGroup
import ohnosequences.awstools.s3.ObjectAddress
import ohnosequences.nispero.utils.Utils
import ohnosequences.nispero.utils.pickles._, upickle._


trait AnyManagerBundle extends AnyBundle {

  type Worker <: AnyWorkerBundle
  val  worker: Worker

  case object workerCompat extends Compatible[Resources#Config#AMI, Worker](
    environment = resources.config.ami,
    bundle = worker,
    metadata = resources.config.metadata
  )

  type Resources <: AnyResourcesBundle
  val  resources: Resources

  val logUploader: LogUploaderBundle
  val controlQueue: ControlQueueBundle
  val terminationDaemon: TerminationDaemonBundle

  val bundleDependencies: List[AnyBundle] = List(controlQueue, terminationDaemon, resources, logUploader)

  val config = resources.config
  val aws = resources.aws
  val logger = Logger(this.getClass)

  def uploadInitialTasks(taskProvider: TasksProvider, initialTasks: ObjectAddress) {

    try {
      logger.info("generating tasks")
      val tasks = taskProvider.tasks(aws.s3)

      // NOTE: It's not used anywhere, but serializing can take too long
      // logger.info("uploading initial tasks to S3")
      // aws.s3.putWholeObject(initialTasks, upickle.default.write(tasks))

      logger.info("adding initial tasks to SQS")
      val inputQueue = aws.sqs.createQueue(resources.config.resourceNames.inputQueue)

      // NOTE: we can send messages in parallel
      tasks.par.foreach { task =>
        inputQueue.sendMessage(upickle.default.write(task))
      }
      aws.s3.putWholeObject(resources.config.tasksUploaded, "")
      logger.info("initial tasks are ready")
    } catch {
      case t: Throwable => logger.error("error during uploading initial tasks"); t.printStackTrace()
    }
  }


  def install: Results = {

    logger.info("manager is started")
    try {

      if (aws.s3.listObjects(config.tasksUploaded.bucket, config.tasksUploaded.key).isEmpty) {
        uploadInitialTasks(config.tasksProvider, config.initialTasks)
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

      Utils.waitForResource[AutoScalingGroup] {
        println("waiting for manager autoscalling")
        aws.as.getAutoScalingGroupByName(groupName)
      }

      logger.info("creating tags")
      Utils.tagAutoScalingGroup(aws.as, groupName, InstanceTags.INSTALLING.value)

      logger.info("starting termination daemon")
      terminationDaemon.TerminationDaemonThread.start()

      controlQueue.run()

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

abstract class ManagerBundle[
  W <: AnyWorkerBundle,
  R <: AnyResourcesBundle
](val controlQueue: ControlQueueBundle,
  val terminationDaemon: TerminationDaemonBundle,
  val logUploader: LogUploaderBundle,
  val worker: W,
  val resources: R
) extends AnyManagerBundle {

  type Worker = W
  type Resources = R
}
