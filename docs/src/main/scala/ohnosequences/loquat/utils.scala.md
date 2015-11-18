
```scala
package ohnosequences.loquat

case object utils {

  import com.typesafe.scalalogging.LazyLogging

  import ohnosequences.awstools.ec2._
  import ohnosequences.awstools.s3._
  import ohnosequences.awstools.autoscaling.{ AutoScaling, AutoScalingGroup }

  import com.amazonaws.services.s3.transfer._
  import com.amazonaws.services.s3.model.{ S3Object => _, _ }
  import com.amazonaws.event._

  import better.files._
  import scala.collection.JavaConversions._
  import scala.util._


  trait AnyStep extends LazyLogging
  case class Step[T](msg: String)(action: => Try[T]) extends AnyStep {

    def execute: Try[T] = {
      logger.debug(msg)
      action.recoverWith {
        case e: Throwable =>
          logger.error(s"Error during ${msg}: \n${e.getMessage}")
          Failure(e)
      }
    }
  }


  class Time(val inSeconds: Long) {
    val millis: Long = inSeconds * 1000
    val seconds: Long = inSeconds
    val minutes: Long = inSeconds / 60
    val hours: Long = inSeconds / (60 * 60)

    def prettyPrint: String = List(
      (hours, "hours"),
      (minutes, "min"),
      (seconds, "sec")
    ).map{ case (value, label) =>
      (if (value > 0) s"${value} ${label}" else "")
    }.mkString
  }

  case class Millis(ms: Long) extends Time(ms / 1000)
  case class Seconds(s: Long) extends Time(s)
  case class Minutes(m: Long) extends Time(m * 60)
  case class   Hours(h: Long) extends Time(h * 60 * 60)



  object InstanceTags {
    val PRODUCT_TAG = InstanceTag("product", "loquat")

    val STATUS_TAG_NAME = "status"

    //for instances
    val RUNNING    = InstanceTag(STATUS_TAG_NAME, "running")
    val INSTALLING = InstanceTag(STATUS_TAG_NAME, "installing")
    val IDLE       = InstanceTag(STATUS_TAG_NAME, "idle")
    val PROCESSING = InstanceTag(STATUS_TAG_NAME, "processing")
    val FINISHING  = InstanceTag(STATUS_TAG_NAME, "finishing")
    val FAILED     = InstanceTag(STATUS_TAG_NAME, "failed")

    val AUTO_SCALING_GROUP = "autoScalingGroup"
  }


  def tagAutoScalingGroup(as: AutoScaling, groupName: String, status: String): Unit = {
    as.createTags(groupName, InstanceTags.PRODUCT_TAG)
    as.createTags(groupName, InstanceTag(InstanceTags.AUTO_SCALING_GROUP, groupName))
    as.createTags(groupName, InstanceTag(InstanceTags.STATUS_TAG_NAME, status))
    as.createTags(groupName, InstanceTag("Name", groupName))
  }

  @scala.annotation.tailrec
  def waitForResource[R](getResource: => Option[R], tries: Int, timeStep: Time) : Option[R] = {
    val resource = getResource

    if (resource.isEmpty && tries <= 0) {
      Thread.sleep(timeStep.inSeconds * 1000)
      waitForResource(getResource, tries - 1, timeStep)
    } else resource
  }


  // This is used for adding loquat artifact metadata to the S3 objects that we are uploading
  case class s3MetadataProvider(metadataMap: Map[String, String]) extends ObjectMetadataProvider {

    def provideObjectMetadata(file: java.io.File, metadata: ObjectMetadata): Unit = {
      // NOTE: not sure that this is needed (for multi-file upload)
      // import java.util.Base64
      // import java.nio.charset.StandardCharsets
      // metadata.setContentMD5(
      //   Base64.getEncoder.encodeToString(file.toScala.md5.toLowerCase.getBytes(StandardCharsets.UTF_8))
      // )
      metadata.setUserMetadata(metadataMap)
    }
  }

  implicit def transferManagerOps(tm: TransferManager):
    TransferManagerOps =
    TransferManagerOps(tm)

  // TODO: use futures here
  case class TransferManagerOps(tm: TransferManager) {

    def download(
      s3Address: AnyS3Address,
      destination: File
    ): Try[File] = {
      println(s"""Dowloading object
        |from: ${s3Address.url}
        |to: ${destination.path}
        |""".stripMargin
      )

      val transfer: Transfer = s3Address match {
        case S3Object(bucket, key) => tm.download(bucket, key, destination.toJava)
        case S3Folder(bucket, key) => tm.downloadDirectory(bucket, key, destination.toJava)
      }

      // This should attach a default progress listener
      transfer.addProgressListener(new ProgressTracker())

      Try {
        // NOTE: this is blocking:
        transfer.waitForCompletion

        // if this was a virtual directory, the destination actually differs:
        s3Address match {
          case S3Object(_, key) => destination
          case S3Folder(_, key) => destination / key
        }
      }
    }

    def upload(
      file: File,
      s3Address: AnyS3Address,
      userMetadata: Map[String, String]
    ): Try[AnyS3Address] = {
      println(s"""Uploading object
        |from: ${file.path}
        |to: ${s3Address.url}
        |""".stripMargin
      )

      val transfer: Transfer = if (file.isDirectory) {
        tm.uploadDirectory(
          s3Address.bucket,
          s3Address.key,
          file.toJava,
          true, // includeSubdirectories
          s3MetadataProvider(userMetadata)
        )
      } else {
        val request = new PutObjectRequest(
          s3Address.bucket,
          s3Address.key,
          file.toJava
        )

        val metadata = new ObjectMetadata()
        metadata.setUserMetadata(userMetadata)

        tm.upload( request.withMetadata(metadata) )
      }

      // This should attach a default progress listener
      transfer.addProgressListener(new ProgressTracker())

      Try {
        // NOTE: this is blocking:
        transfer.waitForCompletion
        s3Address
      }
    }
  }

}

```




[main/scala/ohnosequences/loquat/configs.scala]: configs.scala.md
[main/scala/ohnosequences/loquat/daemons.scala]: daemons.scala.md
[main/scala/ohnosequences/loquat/dataMappings.scala]: dataMappings.scala.md
[main/scala/ohnosequences/loquat/dataProcessing.scala]: dataProcessing.scala.md
[main/scala/ohnosequences/loquat/loquats.scala]: loquats.scala.md
[main/scala/ohnosequences/loquat/managers.scala]: managers.scala.md
[main/scala/ohnosequences/loquat/utils.scala]: utils.scala.md
[main/scala/ohnosequences/loquat/workers.scala]: workers.scala.md
[test/scala/ohnosequences/loquat/dataMappings.scala]: ../../../../test/scala/ohnosequences/loquat/dataMappings.scala.md
[test/scala/ohnosequences/loquat/instructions.scala]: ../../../../test/scala/ohnosequences/loquat/instructions.scala.md