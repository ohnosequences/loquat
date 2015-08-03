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


trait AnyManager extends AnyBundle {

  type Worker <: AnyWorker
  val  worker: Worker

  val resourcesBundle: ResourcesBundle
  val logUploader: LogUploader
  val controlQueueHandler: ControlQueueHandler
  val terminationDaemon: TerminationDaemon
  val aws: AWSBundle

  val metadata: AnyArtifactMetadata = resourcesBundle.aws.metadata

  type AMI = resourcesBundle.aws.AMI
  val  ami = resourcesBundle.aws.ami

  case object workerCompat extends Compatible(ami, worker, metadata)

  //val m: ami.Metadata = resourcesBundle.configuration.metadata.asInstanceOf[ami.Metadata]
  //val metadata = m

  val bundleDependencies: List[AnyBundle] = List(controlQueueHandler, terminationDaemon, resourcesBundle, logUploader, aws)

  val logger = Logger(this.getClass)

  def uploadInitialTasks(taskProvider: TasksProvider, initialTasks: ObjectAddress) {
    try {
      logger.info("generating tasks")
      val tasks = taskProvider.tasks(aws.clients.s3)

      // NOTE: It's not used anywhere, but serializing can take too long
      // logger.info("uploading initial tasks to S3")
      // aws.s3.putWholeObject(initialTasks, upickle.default.write(tasks))

      logger.info("adding initial tasks to SQS")
      val inputQueue = aws.clients.sqs.createQueue(resourcesBundle.resources.inputQueue)

      // NOTE: we can send messages in parallel
      tasks.par.foreach { task =>
        inputQueue.sendMessage(upickle.default.write(task))
      }
      aws.clients.s3.putWholeObject(resourcesBundle.aws.config.tasksUploaded, "")
      logger.info("initial tasks are ready")

    } catch {
      case t: Throwable => logger.error("error during uploading initial tasks"); t.printStackTrace()
    }
  }


  def install: Results = {

    val config = resourcesBundle.aws.config

    logger.info("manager is started")

    try {

      if (aws.clients.s3.listObjects(config.tasksUploaded.bucket, config.tasksUploaded.key).isEmpty) {
        uploadInitialTasks(config.tasksProvider, config.initialTasks)
      } else {
        logger.warn("skipping uploading tasks")
      }

      logger.info("generating workers userScript")

      // val workerUserScript = userScript(worker)
      // val workerUserScript = ami.userScript(metadata, this.fullName, worker.fullName)

      val workersGroup = aws.clients.as.fixAutoScalingGroupUserData(config.resources.workersGroup, workerCompat.userScript)

      logger.info("running workers auto scaling group")
      aws.clients.as.createAutoScalingGroup(workersGroup)

      val groupName = config.resources.workersGroup.name

      Utils.waitForResource[AutoScalingGroup] {
        println("waiting for manager autoscalling")
        aws.clients.as.getAutoScalingGroupByName(groupName)
      }

      logger.info("creating tags")
      Utils.tagAutoScalingGroup(aws.clients.as, groupName, InstanceTags.INSTALLING.value)

      logger.info("starting termination daemon")
      terminationDaemon.TerminationDaemonThread.start()

      controlQueueHandler.run()

      success("manager installed")


    } catch {
      case t: Throwable => {
        t.printStackTrace()
        aws.clients.ec2.getCurrentInstance.foreach(_.terminate())
        failure("manager fails")
      }
    }
  }
}

abstract class Manager[W <: AnyWorker](
  val controlQueueHandler: ControlQueueHandler,
  val terminationDaemon: TerminationDaemon,
  val resourcesBundle: ResourcesBundle,
  val logUploader: LogUploader,
  val aws: AWSBundle,
  val worker: W
) extends AnyManager { type Worker = W }
