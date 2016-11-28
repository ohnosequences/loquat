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
