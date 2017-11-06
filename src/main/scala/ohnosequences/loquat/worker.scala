package ohnosequences.loquat

import utils._, files._
import ohnosequences.statika._
import ohnosequences.datasets._
import ohnosequences.awstools._, s3._, ec2._, regions._
import com.amazonaws.services.sqs.model.ReceiptHandleIsInvalidException
import ohnosequences.awstools._, sqs._, s3._, ec2._

import com.typesafe.scalalogging.LazyLogging
import scala.concurrent._, duration._
import scala.util, util.Try
import java.util.concurrent.ScheduledFuture


trait AnyWorkerBundle extends AnyBundle {

  type DataProcessingBundle <: AnyDataProcessingBundle
  val  instructionsBundle: DataProcessingBundle

  type Config <: AnyLoquatConfig
  val  config: Config

  val scheduler = Scheduler(3)

  lazy val loggerBundle = LogUploaderBundle(config, scheduler)

  val bundleDependencies: List[AnyBundle] = List(
    loggerBundle,
    instructionsBundle
  )

  lazy val ctx = GeneralContext(config, loggerBundle, instructionsBundle, scheduler)

  def instructions: AnyInstructions = LazyTry {
    import ctx._
    // Await.result(taskLoop(ExecutionContext.Implicits.global), 12.hours)
    taskLoop(ExecutionContext.Implicits.global)
  }
}

abstract class WorkerBundle[
  I <: AnyDataProcessingBundle,
  C <: AnyLoquatConfig
](val instructionsBundle: I,
  val config: C
) extends AnyWorkerBundle {

  type DataProcessingBundle = I
  type Config = C
}


/* General context per-worker */
case class GeneralContext(
  val config: AnyLoquatConfig,
  val loggerBundle: LogUploaderBundle,
  val instructionsBundle: AnyDataProcessingBundle,
  val scheduler: Scheduler
) extends LazyLogging { ctx =>

  lazy val workingDir: File = file("/media/ephemeral0/applicator/loquat")
  lazy val inputDir:  File = workingDir / "input"
  lazy val outputDir: File = workingDir / "output"

  lazy val aws = AWSClients.withRegion(config.region)

  val inputQueue  = aws.sqs.getQueue(config.resourceNames.inputQueue).get
  val errorQueue  = aws.sqs.getQueue(config.resourceNames.errorQueue).get
  val outputQueue = aws.sqs.getQueue(config.resourceNames.outputQueue).get

  val transferManager = aws.s3.createTransferManager
  // TODO: it should be shutdown explicitly at some point
  // transferManager.shutdown()

  val instance = aws.ec2.getCurrentInstance.get

  /* This metadata will be attached to each uploaded S3 object */
  val s3Metadata: Map[String, String] = Map(
    "artifact-org"     -> config.metadata.organization,
    "artifact-name"    -> config.metadata.artifact,
    "artifact-version" -> config.metadata.version,
    "artifact-url"     -> config.metadata.artifactUrl
  )

  /* This is a _full_ iteration of the task processing loop */
  def taskLoop(implicit ec: ExecutionContext): Future[FiniteDuration] = {

    receiveMessage.flatMap { message =>
      TaskContext(
        message,
        Deadline.now
      )(ctx).processTask
    }.flatMap { _ => taskLoop }
  }


  /* Waits for a task-message from the input queue. It is supposed to wait and send requests as long as needed. */
  @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
  def receiveMessage(implicit ec: ExecutionContext): Future[Message] = Future.fromTry {
    logger.info("Data processor is waiting for new data...")

    inputQueue.poll(
      timeout = Duration.Inf,
      amountLimit = Some(1),
      adjustRequest = { _.withWaitTimeSeconds(10) }
    ).map { _.head }
  }

}


/* Local context per-task */
case class TaskContext(
  val message: Message,
  val startTime: Deadline
)(val ctx: GeneralContext) extends LazyLogging {
  import ctx._

  val dataMapping = SimpleDataMapping.deserialize(message.body)

  def timeSpent: FiniteDuration = -startTime.timeLeft

  val keeper: ScheduledFuture[_] = scheduler.repeat(
    after = 1.second,
    every = 7.seconds
  ) {
    logger.debug(s"Keeping message ${dataMapping.id} in flight (${timeSpent.toSeconds}s)")
    message.changeVisibility(8.seconds).recover {
      case ex: ReceiptHandleIsInvalidException => {
        logger.warn(s"Task ${dataMapping.id} is already done by somebody")
        keeper.cancel(true)
        ()
      }
    }
  }

  /* This is the main method that conbines all the other methods */
  def processTask(implicit ec: ExecutionContext): Future[FiniteDuration] = {
    logger.info(s"Started task ${dataMapping.id}")

    Future(taskPipeline).map { _ =>
      keeper.cancel(true)
      logger.info(s"Task ${dataMapping.id} took ${timeSpent.toSeconds}s")
      timeSpent
    }
  }

  def taskPipeline(): Unit = {
    val input = prepareInputData
    val output = processFiles(input)
    finishTask(output)
  }

  /* This method downloads one input data resource and returns the created file */
  private def downloadInput(
    name: String,
    resource: AnyRemoteResource
  ): File = resource match {
    /* - if the resource is a message, we just write it to a file */
    case MessageResource(msg) => {
      logger.debug(s"Input [${name}]: writing message to a file")
      val file = (inputDir / name).overwrite(msg)
      logger.debug(s"Input [${name}]: written to ${file.path}")
      file
    }
    /* - if the resource is an S3 object/folder, we download it */
    case S3Resource(s3Address) => {
      // NOTE: if it is an S3 folder, destination will be inputDir/name/<s3Address.key>/
      logger.debug(s"Input [${name}]: downloading [${s3Address}]")
      import ohnosequences.awstools.s3._
      val file = transferManager.download(
        s3Address,
        inputDir / name
      ).get
      logger.debug(s"Input [${name}]: downloaded to [${file.path}]")
      file
    }
  }

  /* This downloads input files for the given datamapping. Multiple files will be downloaded in parallel. */
  // def prepareInputData(implicit ec: ExecutionContext): Future[Map[String, File]] = {
  def prepareInputData(): Map[String, File] = {
    logger.debug(s"Cleaning up...")
    workingDir.deleteRecursively()
    inputDir.createDirectory
    outputDir.createDirectory

    logger.debug("Downloading input data...")
    // Future.traverse(dataMapping.inputs) { case (name, resource) =>
      // downloadInput(name, resource).map { name -> _ }
    dataMapping.inputs.map { case (name, resource) =>
      name -> downloadInput(name, resource)
    }
    // }.map { _.toMap }
  }

  def processFiles(
    inputFiles: Map[String, File]
  ): Map[String, File] = {
    logger.debug("Processing data...")
    instructionsBundle.runProcess(workingDir, inputFiles) match {
      case Success(_, outputFiles) => outputFiles
      case Failure(errors) => {

        logger.error(s"Data processing failed, publishing it to the error queue")
        errorQueue.sendOne(
          // TODO: attach normall log
          ProcessingResult(instance.id, errors.mkString("\n")).toString
        )
        // TODO: make a specific exception
        throw new Throwable(errors.mkString("\n"))
      }
    }
  }

  /* This method uploads one output file and returns the destination S3 address or `None` if the files was empty and could be skept */
  private def uploadOutput(
    file: File,
    destination: AnyS3Address
  ): Option[AnyS3Address] = {
    if (config.skipEmptyResults && file.isEmpty) {
      logger.info(s"Output file [${file.getName}] is empty. Skipping it.")
      None
    } else {
      logger.info(s"Publishing output object: [${file.getName}]")
      transferManager.upload(
        file,
        destination,
        s3Metadata,
        true // silent
      ).toOption
    }
  }

  /* This method uploads all output files, sends a success-message and (!) deletes the task-message from the input queue */
  // def finishTask(outputFiles: Map[String, File])(implicit ec: ExecutionContext): Future[Unit] = {
  def finishTask(outputFiles: Map[String, File]): Unit = {
    // NOTE: this method could be split, but the point of it is to control that task is successfully finished _only_ if the output files are successfully uploaded. Then we can delete the task-message and the task is "announced" as done.
    logger.info("Uploading output files...")
    // Future.traverse(outputFiles) { case (name, file) =>
      outputFiles.map { case (name, file) =>
        uploadOutput(file, dataMapping.outputs(name).resource)
      }
    // }.map { _ =>
      notifyOutputQueue()
    // }.map { _ =>
      message.delete().get
    // }
  }

  def notifyOutputQueue(): Unit = {
    logger.info("Finished uploading output files. Publishing message to the output queue.")
    outputQueue.sendOne(
      // TODO: attach normall log
      ProcessingResult(instance.id, dataMapping.id).toString
    ).get
  }

  /* Publishes an error message to the SQS queue */
  def publishError(msg: String): Try[MessageId] = {
    logger.error(msg)

    // TODO: should publish relevant part of the log
    // TODO: construct a JSON?
    errorQueue.sendOne(s"Instance: ${instance.id}; Task: ${dataMapping.id}; Reason: ${msg}")
  }

  /* Enter the void... */
  def suicide(reason: Throwable): Unit = {
    // TODO: SNS notifications on critical failures
    publishError(s"Instance is terminated due to a fatal exception: ${reason}")
    instance.terminate
    // throw the suicide note
    throw reason
  }

}
