package ohnosequences.loquat

import utils._

import ohnosequences.statika._
import ohnosequences.datasets._

import com.amazonaws.services.s3.transfer.TransferManager
import ohnosequences.awstools._, sqs._, s3._, ec2._

import com.typesafe.scalalogging.LazyLogging
import better.files._
import scala.concurrent._, duration._
import scala.util.Try
import upickle.Js


trait AnyWorkerBundle extends AnyBundle {

  type DataProcessingBundle <: AnyDataProcessingBundle
  val  instructionsBundle: DataProcessingBundle

  type Config <: AnyLoquatConfig
  val  config: Config

  val scheduler = Scheduler(2)

  val bundleDependencies: List[AnyBundle] = List(
    instructionsBundle,
    LogUploaderBundle(config, scheduler)
  )

  def instructions: AnyInstructions = LazyTry {
    ???
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


case object WorkContext(
  val config: AnyLoquatConfig,
  val instructionsBundle: AnyDataProcessingBundle
) {

  final val workingDir: File = file"/media/ephemeral0/applicator/loquat"

  /* AWS related things */
  lazy val aws = instanceAWSClients(config)

  val inputQueue  = aws.sqs.getQueue(config.resourceNames.inputQueue).get
  val errorQueue  = aws.sqs.getQueue(config.resourceNames.errorQueue).get
  val outputQueue = aws.sqs.getQueue(config.resourceNames.outputQueue).get

  val instance = aws.ec2.getCurrentInstance.get
}

case class Workflow(ctx: WorkContext) extends LazyLogging {
  import ctx._

  def receiveMessage(): Future[Message] = Future {
    logger.info("Data processor is waiting for new data...")

    inputQueue.poll(
      timeout = Duration.Inf,
      amountLimit = Some(1),
      adjustRequest = { _.withWaitTimeSeconds(10) }
    ).get.head
  }

  // upickle.default.read[SimpleDataMapping](message.body)

    // logger.info("DataProcessor started at " + instance.id)

  /* This method downloads one input data resource and returns the created file */
  def downloadInput(name: String, resource: AnyRemoteResource): Future[File] = resource match {
    /* - if the resource is a message, we just write it to a file */
    case MessageResource(msg) => Future {
      logger.debug(s"Input [${name}]: writing message to a file")
      (inputDir / name).createIfNotExists().overwrite(msg)
    }
    /* - if the resource is an S3 object/folder, we download it */
    case S3Resource(s3Address) => Future.fromTry {
      // NOTE: if it is an S3 folder, destination will be inputDir/name/<s3Address.key>/
      logger.debug(s"Input [${name}]: downloading [${s3Address}]")
      transferManager.download(
        s3Address,
        (inputDir / name).toJava,
        silent = false // minimal logging
      ).get.toScala
    }
  }

  /* This downloads input files for the given datamapping. Multiple files will be downloaded in parallel. */
  def prepareInputData(dataMapping: SimpleDataMapping): Future[Map[String, File]] = Future {

    logger.debug(s"Cleaning up and preparing the working directory: ${workingDir.path}")
    workingDir.createIfNotExists(asDirectory = true, createParents = true)
    workingDir.clear()
  }.flatMap { _ =>

    logger.debug("Downloading input data...")
    Future.traverse(dataMapping.inputs) { case (name, resource) =>
      downloadInput(name, resource).map { name -> _ }
    }
  }
}
