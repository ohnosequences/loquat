package ohnosequences.loquat

import utils._

import ohnosequences.statika._

import com.typesafe.scalalogging.LazyLogging
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

import ohnosequences.awstools.s3._
import scala.concurrent._
import java.util.concurrent._
import scala.util.Try
import better.files._


private[loquat]
case class LogUploaderBundle(
  val config: AnyLoquatConfig,
  val scheduler: Scheduler
) extends Bundle() with LazyLogging {

  lazy val aws = instanceAWSClients(config)

  lazy val logFile = file"/root/log.txt"

  lazy val bucket = config.resourceNames.bucket
  lazy val logS3: Option[S3Object] = aws.ec2.getCurrentInstanceId.map { id =>
    S3Object(bucket, s"${config.loquatId}/${id}.log")
  }
  // getOrElse {
  //   logger.error(s"Failed to get current instance ID")
  // }
  lazy val tm = utils.TransferManagerOps(aws.s3.createTransferManager)

  def uploadLog(): Unit = logS3.map { destination =>
    tm.upload(logFile, destination, Map())
    ()
  }.getOrElse {
    logger.error(s"Failed to upload the log to [${bucket}]")
  }

  def instructions: AnyInstructions = LazyTry[Unit] {
    if (aws.s3.doesBucketExist(bucket)) {
      scheduler.repeat(
        after = 30.seconds,
        every = 30.seconds
      )(uploadLog)
      Success("Log uploader daemon started", ())
    }
    else Failure(s"Bucket [${bucket}] doesn't exist")
  }
}
