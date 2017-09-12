package ohnosequences.loquat

import utils._, files._
import ohnosequences.statika._
import ohnosequences.datasets._
import ohnosequences.awstools._, sqs._, s3._, ec2._, regions._
import com.amazonaws.services.s3.transfer.TransferManager
import com.amazonaws.services.sqs.model.ReceiptHandleIsInvalidException
import ohnosequences.awstools._, sqs._, s3._, ec2._

import com.typesafe.scalalogging.LazyLogging
import scala.concurrent._, duration._
import scala.util, util.Try
import java.util.concurrent.{ Executors, ExecutorService }

import ExecutionContext.Implicits.global

trait AnyWorkerBundle extends AnyBundle {

  type DataProcessingBundle <: AnyDataProcessingBundle
  val  instructionsBundle: DataProcessingBundle

  type Config <: AnyLoquatConfig
  val  config: Config

  val scheduler = Scheduler(2)

  lazy val loggerBundle = LogUploaderBundle(config, scheduler)

  val bundleDependencies: List[AnyBundle] = List(
    loggerBundle,
    instructionsBundle
  )

  lazy val ctx = GeneralContext(config, loggerBundle, instructionsBundle)

  def instructions: AnyInstructions = LazyTry {
    import ctx._
    // TODO: any better way to loop this?
    taskIteration.flatMap { _ => taskIteration }
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
  val instructionsBundle: AnyDataProcessingBundle
) extends LazyLogging { ctx =>

  lazy val workingDir: File = file("/media/ephemeral0/applicator/loquat")
  lazy val inputDir:  File = workingDir / "input"
  lazy val outputDir: File = workingDir / "output"

  /* AWS related things */
  lazy val aws = AWSClients(config.region)

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

  /* Execution context for the futures */
  // TODO: probably download/upload futures need their own execution context (and AWS clients with more connectinos open)
  // implicit lazy val globalContext = ExecutionContext.Implicits.global
  // implicit lazy val fixedThreadPoolExecutionContext: ExecutionContext = {
  //   val fixedThreadPool: ExecutorService = Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors * 6)
  //   ExecutionContext.fromExecutor(fixedThreadPool)
  //   // TODO: use publishError as a reporter
  // }


  /* This is a _full_ iteration of the task processing loop */
  def taskIteration(): Future[FiniteDuration] = {

    receiveMessage.flatMap { message =>
      val taskCtx = TaskContext(message, Deadline.now)(ctx)
      val futureResult = taskCtx.processTask()

      logger.info(s"Started task ${taskCtx.dataMapping.id}")

      // TODO: can this cause a situation when all threads are ocupied by the futureResult's inner futures and there's no thread left for the keepMessageInFlight, which is supposed to run in parallel with futureResult?
      Future.firstCompletedOf(Seq(
        futureResult,
        taskCtx.keepMessageInFlight(futureResult)
      ))
    }
  }


  /* Waits for a task-message from the input queue. It is supposed to wait and send requests as long as needed. */
  def receiveMessage(): Future[Message] = Future.fromTry {
    logger.info("Data processor is waiting for new data...")

    inputQueue.poll(
      timeout = Duration.Inf,
      amountLimit = Some(1),
      adjustRequest = { _.withWaitTimeSeconds(10) }
    ).map { _.head }
  }
  // NOTE: for whatever reason it fails, the best we can do is just try again
  //.fallbackTo(receiveMessage())


}


/* Local context per-task */
case class TaskContext(
  val message: Message,
  val startTime: Deadline
)(val ctx: GeneralContext) extends LazyLogging {
  import ctx._

  val dataMapping = SimpleDataMapping.deserialize(message.body)

  def timeSpent: FiniteDuration = -startTime.timeLeft


  /* This is the main method that conbines all the other methods */
  def processTask(): Future[FiniteDuration] = {

    prepareInputData()
      .flatMap(processFiles)
      .flatMap(finishTask)
      .map { _ =>
        logger.info(s"Task ${dataMapping.id} took ${timeSpent.toSeconds}s")
        timeSpent
      }
  }


  /* This method downloads one input data resource and returns the created file */
  private def downloadInput(name: String, resource: AnyRemoteResource): Future[File] = resource match {
    /* - if the resource is a message, we just write it to a file */
    case MessageResource(msg) => Future {
      logger.debug(s"Input [${name}]: writing message to a file")
      val f = (inputDir / name).overwrite(msg)
      logger.debug(s"Input [${name}]: written to ${f.path}")
      f
    }
    /* - if the resource is an S3 object/folder, we download it */
    case S3Resource(s3Address) => Future.fromTry {
      // NOTE: if it is an S3 folder, destination will be inputDir/name/<s3Address.key>/
      logger.debug(s"Input [${name}]: downloading [${s3Address}]")
      import ohnosequences.awstools.s3._
      transferManager.download(
        s3Address,
        inputDir / name
      )
    }
  }

  /* This downloads input files for the given datamapping. Multiple files will be downloaded in parallel. */
  def prepareInputData(): Future[Map[String, File]] = Future {

    logger.debug(s"Cleaning up and preparing the working directory: ${workingDir.path}")
    workingDir.deleteRecursively()
    workingDir.createDirectory
  }.map { _ =>

    logger.debug("Downloading input data...")
    // FIXME: this parallel downloading gets stuck on MessageResource file-writing for some reason
    // Future.traverse(dataMapping.inputs) { case (name, resource) =>
    //   downloadInput(name, resource).map { name -> _ }
    // }.map { _.toMap }

    // this doesn't work either:
    // dataMapping.inputs.foldLeft(
    //   Future.successful { Map[String, File]() }
    // ) { case (accF, (name, resource)) =>
    //   for {
    //     acc <- accF
    //     file <- downloadInput(name, resource)
    //   } yield acc + (name -> file)
    // }

    dataMapping.inputs.map { case (name, resource) =>
      name -> Await.result(downloadInput(name, resource), 5.minutes)
    }
  }

  def processFiles(inputFiles: Map[String, File]): Future[Map[String, File]] = Future {

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
  private def uploadOutput(file: File, destination: AnyS3Address): Future[Option[AnyS3Address]] = {

    // FIXME: file.isEmpty may fail on compressed files!
    if (config.skipEmptyResults && file.isEmpty) Future.successful {
      logger.info(s"Output file [${file.getName}] is empty. Skipping it.")
      None
    } else Future.fromTry {

      logger.info(s"Publishing output object: [${file.getName}]")
      transferManager.upload(
        file,
        destination,
        s3Metadata,
        true // silent
      ).map { Some(_) }
    }
  }

  /* This method uploads all output files, sends a success-message and (!) deletes the task-message from the input queue */
  def finishTask(outputFiles: Map[String, File]): Future[Unit] = {
    // NOTE: this method could be split, but the point of it is to control that task is successfully finished _only_ if the output files are successfully uploaded. Then we can delete the task-message and the task is "announced" as done.
    // logger.info("Uploading output files...")
    Future.traverse(outputFiles) { case (name, file) =>

      uploadOutput(file, dataMapping.outputs(name).resource)

    }.flatMap { outputObjects =>

      logger.info("Finished uploading output files. Publishing message to the output queue.")
      Future.fromTry {
        outputQueue.sendOne(
          // TODO: attach normall log
          ProcessingResult(instance.id, dataMapping.id).toString
        )
      }
    }.flatMap { _ =>
      Future.fromTry { message.delete() }
    }
  }


  /* This method is supposed to run in parallel with `processTask` to keep its message in-flight */
  def keepMessageInFlight(futureResult: Future[FiniteDuration]): Future[FiniteDuration] = {
    val deadline: Deadline =
      config.terminationConfig
        .taskProcessingTimeout // either configured timeout
        .getOrElse(12.hours)   // or just the global one
        .fromNow

    val messageTimeout = inputQueue.visibilityTimeout

    /* This future will be cycling until the task is done or timeout is exceeded */
    Future {
      // TODO: is there a better way to loop it?
      while(!futureResult.isCompleted) {

        /* We sleep a bit less to prolong visibility timeout _before_ the message will return to the queue */
        // NOTE: probably this need some more careful "time-padding"
        logger.debug(s"Going to sleep... zzzZzzZZZzzzz")
        sleep(messageTimeout * 0.95)
        logger.debug(s"Slept ${messageTimeout * 0.95}")

        /* - if by the time we wake up everything is already done, no need to do anything else: we return the result (although it won't be used anywhere) */
        if (futureResult.isCompleted) futureResult
        /* - if it takes too much time we fail */
        else if(deadline.isOverdue) {
          // TODO: publish error message
          suicide(new TimeoutException("Task processing timeout exceeded"))
        /* - otherwise, data is still being processed and we try to keep the message in-flight */
        } else {

          logger.info(s"Keeping message ${dataMapping.id} in flight")
          message.changeVisibility(messageTimeout).recover {

            /* The message likely doesn't exist, i.e. already successfuly processed (possibly by somebody else) */
            // NOTE: as we don't have any civilized way to stop ongoing parallel computation, we just terminate the instance
            case ex: ReceiptHandleIsInvalidException =>
              suicide(new ReceiptHandleIsInvalidException(s"Task ${dataMapping.id} is already done by somebody"))

            /* The message exists, but is not in flight, i.e. at some point we failed to keep it in-flight */
            // NOTE: we can ignore this case because once the task will be finished (by this or another instance) it will be deleted and the other case will terminate the slowpoke worker
            // case ex: MessageNotInflightException =>
          }
        }
      }

      timeSpent
    }
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
