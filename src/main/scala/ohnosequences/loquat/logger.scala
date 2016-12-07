package ohnosequences.loquat

import utils._

import ohnosequences.statika._
import ohnosequences.awstools._, s3._, ec2._
// import com.amazonaws.services.s3.model.PutObjectResult

import com.typesafe.scalalogging.LazyLogging
import java.util.concurrent._
import scala.concurrent._, duration._
import scala.util.Try
import better.files._


private[loquat]
case class LogUploaderBundle(
  val config: AnyLoquatConfig,
  val scheduler: Scheduler
) extends Bundle() with LazyLogging {

  lazy val s3 = instanceAWSClients(config).s3

  lazy val logFile = file"/root/log.txt"

  lazy val instanceID = getLocalMetadata("instance-id").getOrElse {
    sys.error("Failed to get instance ID")
  }

  lazy val logS3: S3Object = config.resourceNames.logs / s"${instanceID}.log"

  def instructions: AnyInstructions = LazyTry[Unit] {
    scheduler.repeat(
      after = 30.seconds,
      every = 30.seconds
    ) {
      s3.putObject(logS3.bucket, logS3.key, logFile.toJava)
    }
  }
}
