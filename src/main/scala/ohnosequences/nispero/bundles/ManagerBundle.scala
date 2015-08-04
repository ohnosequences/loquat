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

  type ResourcesBundle <: AnyResourcesBundle
  val  resourcesBundle: ResourcesBundle

  val logUploaderBundle: LogUploaderBundle
  val controlQueueHandler: ControlQueueBundle
  val terminationDaemon: TerminationDaemonBundle

  val metadata: AnyArtifactMetadata = resourcesBundle.config.metadata

  type AMI = resourcesBundle.config.AMI
  val  ami = resourcesBundle.config.ami

  val awsClients = resourcesBundle.awsClients

  case object workerCompat extends Compatible(ami, worker, metadata)

  val bundleDependencies: List[AnyBundle] = List(controlQueueHandler, terminationDaemon, resourcesBundle, logUploaderBundle)

  val logger = Logger(this.getClass)

  def uploadInitialTasks(taskProvider: TasksProvider, initialTasks: ObjectAddress) {
    try {
      logger.info("generating tasks")
      val tasks = taskProvider.tasks(awsClients.s3)

      // NOTE: It's not used anywhere, but serializing can take too long
      // logger.info("uploading initial tasks to S3")
      // aws.s3.putWholeObject(initialTasks, upickle.default.write(tasks))

      logger.info("adding initial tasks to SQS")
      val inputQueue = awsClients.sqs.createQueue(resourcesBundle.config.resourceNames.inputQueue)

      // NOTE: we can send messages in parallel
      tasks.par.foreach { task =>
        inputQueue.sendMessage(upickle.default.write(task))
      }
      awsClients.s3.putWholeObject(resourcesBundle.config.tasksUploaded, "")
      logger.info("initial tasks are ready")

    } catch {
      case t: Throwable => logger.error("error during uploading initial tasks"); t.printStackTrace()
    }
  }


  def install: Results = {

    val config = resourcesBundle.config

    logger.info("manager is started")

    try {

      if (awsClients.s3.listObjects(config.tasksUploaded.bucket, config.tasksUploaded.key).isEmpty) {
        uploadInitialTasks(config.tasksProvider, config.initialTasks)
      } else {
        logger.warn("skipping uploading tasks")
      }

      logger.info("generating workers userScript")

      // val workerUserScript = userScript(worker)
      // val workerUserScript = ami.userScript(metadata, this.fullName, worker.fullName)

      val workersGroup = awsClients.as.fixAutoScalingGroupUserData(config.workersAutoScalingGroup, workerCompat.userScript)

      logger.info("running workers auto scaling group")
      awsClients.as.createAutoScalingGroup(workersGroup)

      val groupName = config.workersAutoScalingGroup.name

      Utils.waitForResource[AutoScalingGroup] {
        println("waiting for manager autoscalling")
        awsClients.as.getAutoScalingGroupByName(groupName)
      }

      logger.info("creating tags")
      Utils.tagAutoScalingGroup(awsClients.as, groupName, InstanceTags.INSTALLING.value)

      logger.info("starting termination daemon")
      terminationDaemon.TerminationDaemonThread.start()

      controlQueueHandler.run()

      success("manager installed")


    } catch {
      case t: Throwable => {
        t.printStackTrace()
        awsClients.ec2.getCurrentInstance.foreach(_.terminate())
        failure("manager fails")
      }
    }
  }
}

abstract class ManagerBundle[
  W <: AnyWorkerBundle,
  R <: AnyResourcesBundle
](val controlQueueHandler: ControlQueueBundle,
  val terminationDaemon: TerminationDaemonBundle,
  val logUploaderBundle: LogUploaderBundle,
  val worker: W,
  val resourcesBundle: R
) extends AnyManagerBundle {

  type Worker = W
  type ResourcesBundle = R
}
