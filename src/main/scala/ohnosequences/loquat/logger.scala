package ohnosequences.loquat

import utils._

import ohnosequences.statika._

import com.typesafe.scalalogging.LazyLogging
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

import ohnosequences.awstools.s3._
import java.util.concurrent._
import scala.util.Try
import better.files._


private[loquat]
case class LogUploaderBundle(
  val config: AnyLoquatConfig,
  val scheduler: Scheduler
) extends Bundle() with LazyLogging {

  lazy val aws = instanceAWSClients(config)

  val logFile = file"/root/log.txt"
  val bucket = config.resourceNames.bucket

  def uploadLog(): Unit = Try {
    aws.ec2.getCurrentInstanceId.get
  }.map { id =>
    aws.s3.uploadFile(S3Object(bucket, s"${config.loquatId}/${id}.log"), logFile.toJava)
    ()
  }.getOrElse {
    logger.error(s"Failed to upload the log to the bucket [${bucket}]")
  }

  def instructions: AnyInstructions = LazyTry[Unit] {
    if (aws.s3.bucketExists(bucket)) {
      scheduler.repeat(
        after = 30.seconds,
        every = 30.seconds
      )(uploadLog)
      Success("Log uploader daemon started", ())
    }
    else Failure(s"Bucket [${bucket}] doesn't exist")
  }
}
