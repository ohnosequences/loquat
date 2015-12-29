
```scala
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

```




[test/scala/ohnosequences/loquat/test/dataProcessing.scala]: ../../../../test/scala/ohnosequences/loquat/test/dataProcessing.scala.md
[test/scala/ohnosequences/loquat/test/md5.scala]: ../../../../test/scala/ohnosequences/loquat/test/md5.scala.md
[test/scala/ohnosequences/loquat/test/dataMappings.scala]: ../../../../test/scala/ohnosequences/loquat/test/dataMappings.scala.md
[test/scala/ohnosequences/loquat/test/data.scala]: ../../../../test/scala/ohnosequences/loquat/test/data.scala.md
[test/scala/ohnosequences/loquat/test/config.scala]: ../../../../test/scala/ohnosequences/loquat/test/config.scala.md
[main/scala/ohnosequences/loquat/dataProcessing.scala]: dataProcessing.scala.md
[main/scala/ohnosequences/loquat/terminator.scala]: terminator.scala.md
[main/scala/ohnosequences/loquat/configs/user.scala]: configs/user.scala.md
[main/scala/ohnosequences/loquat/configs/resources.scala]: configs/resources.scala.md
[main/scala/ohnosequences/loquat/configs/general.scala]: configs/general.scala.md
[main/scala/ohnosequences/loquat/configs/autoscaling.scala]: configs/autoscaling.scala.md
[main/scala/ohnosequences/loquat/configs/termination.scala]: configs/termination.scala.md
[main/scala/ohnosequences/loquat/configs/loquat.scala]: configs/loquat.scala.md
[main/scala/ohnosequences/loquat/loquats.scala]: loquats.scala.md
[main/scala/ohnosequences/loquat/utils.scala]: utils.scala.md
[main/scala/ohnosequences/loquat/dataMappings.scala]: dataMappings.scala.md
[main/scala/ohnosequences/loquat/worker.scala]: worker.scala.md
[main/scala/ohnosequences/loquat/logger.scala]: logger.scala.md
[main/scala/ohnosequences/loquat/manager.scala]: manager.scala.md