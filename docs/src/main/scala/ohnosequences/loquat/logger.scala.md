
```scala
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

```




[main/scala/ohnosequences/loquat/configs/autoscaling.scala]: configs/autoscaling.scala.md
[main/scala/ohnosequences/loquat/configs/awsClients.scala]: configs/awsClients.scala.md
[main/scala/ohnosequences/loquat/configs/general.scala]: configs/general.scala.md
[main/scala/ohnosequences/loquat/configs/loquat.scala]: configs/loquat.scala.md
[main/scala/ohnosequences/loquat/configs/resources.scala]: configs/resources.scala.md
[main/scala/ohnosequences/loquat/configs/termination.scala]: configs/termination.scala.md
[main/scala/ohnosequences/loquat/configs/user.scala]: configs/user.scala.md
[main/scala/ohnosequences/loquat/dataMappings.scala]: dataMappings.scala.md
[main/scala/ohnosequences/loquat/dataProcessing.scala]: dataProcessing.scala.md
[main/scala/ohnosequences/loquat/logger.scala]: logger.scala.md
[main/scala/ohnosequences/loquat/loquats.scala]: loquats.scala.md
[main/scala/ohnosequences/loquat/manager.scala]: manager.scala.md
[main/scala/ohnosequences/loquat/terminator.scala]: terminator.scala.md
[main/scala/ohnosequences/loquat/utils.scala]: utils.scala.md
[main/scala/ohnosequences/loquat/worker.scala]: worker.scala.md
[test/scala/ohnosequences/loquat/test/config.scala]: ../../../../test/scala/ohnosequences/loquat/test/config.scala.md
[test/scala/ohnosequences/loquat/test/data.scala]: ../../../../test/scala/ohnosequences/loquat/test/data.scala.md
[test/scala/ohnosequences/loquat/test/dataMappings.scala]: ../../../../test/scala/ohnosequences/loquat/test/dataMappings.scala.md
[test/scala/ohnosequences/loquat/test/dataProcessing.scala]: ../../../../test/scala/ohnosequences/loquat/test/dataProcessing.scala.md
[test/scala/ohnosequences/loquat/test/md5.scala]: ../../../../test/scala/ohnosequences/loquat/test/md5.scala.md