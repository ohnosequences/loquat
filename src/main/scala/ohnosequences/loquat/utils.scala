package ohnosequences.loquat

case object utils {

  import ohnosequences.awstools.ec2._
  import ohnosequences.awstools.s3._
  import ohnosequences.awstools.autoscaling.{ AutoScaling, AutoScalingGroup }
  // import com.amazonaws.services.autoscaling.model._
  import com.typesafe.scalalogging.LazyLogging
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
      s3MetadataProvider: ObjectMetadataProvider
    ): Try[AnyS3Address] = {
      val transfer: Transfer = if (file.isDirectory) {
        tm.uploadDirectory(
          s3Address.bucket,
          s3Address.key,
          file.toJava,
          true, // includeSubdirectories
          s3MetadataProvider
        )
      } else {
        tm.uploadFileList(
          s3Address.bucket,
          s3Address.key,
          file.parent.toJava,
          Seq(file.toJava),
          s3MetadataProvider
        )
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
