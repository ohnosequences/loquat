package ohnosequences.loquat

import utils._

import ohnosequences.statika._
import ohnosequences.awstools._, s3._, ec2._, sns._
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

  lazy val aws = instanceAWSClients(config)

  lazy val logFile = file"/log.txt"

  lazy val instanceID = getLocalMetadata("instance-id").getOrElse {
    sys.error("Failed to get instance ID")
  }

  lazy val logS3: S3Object = config.resourceNames.logs / s"${instanceID}.log"

  def uploadLog(): Try[Unit] = Try {
    aws.s3.putObject(logS3.bucket, logS3.key, logFile.toJava)
    logger.info(s"Uploaded log to S3: [${logS3}]")
  }.recover { case e =>
    logger.error(s"Couldn't upload log to S3: ${e}")
  }

  def instructions: AnyInstructions = LazyTry[Unit] {
    scheduler.repeat(
      after = 30.seconds,
      every = 30.seconds
    )(uploadLog)
  }

  def failureNotification(subject: String): Try[String] = {

    val logTail = logFile.lines.toSeq.takeRight(20).mkString("\n") // 20 last lines

    val tempLinkText: String = aws.s3.generateTemporaryLink(logS3, 1.day).map { url =>
      s"Temporary download link: <${url}>"
    }.getOrElse("")

    val message = s"""${subject}. If it's a fatal failure, you should manually undeploy the loquat.
      |Full log is at <${logS3}>. ${tempLinkText}
      |Here is its tail:
      |
      |[...]
      |${logTail}
      |""".stripMargin

    aws.sns
      .getOrCreateTopic(config.resourceNames.notificationTopic)
      .flatMap { _.publish(message, subject) }
  }
}
