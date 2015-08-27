package ohnosequences.loquat

case object utils {

  import java.io.{PrintWriter, File}
  import ohnosequences.awstools.ec2.Tag
  import ohnosequences.awstools.autoscaling.{ AutoScaling, AutoScalingGroup }
  import com.amazonaws.services.autoscaling.model._
  import com.typesafe.scalalogging.LazyLogging
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


  class Time(val inSeconds: Int)
  case class Seconds(s: Int) extends Time(s)
  case class Minutes(m: Int) extends Time(m * 60)
  case class   Hours(h: Int) extends Time(h * 60 * 60)



  object InstanceTags {
    val PRODUCT_TAG = Tag("product", "loquat")

    val STATUS_TAG_NAME = "status"

    //for instances
    val RUNNING = Tag(STATUS_TAG_NAME, "running")
    val INSTALLING = Tag(STATUS_TAG_NAME, "installing")
    val IDLE = Tag(STATUS_TAG_NAME, "idle")
    val PROCESSING = Tag(STATUS_TAG_NAME, "processing")
    val FINISHING = Tag(STATUS_TAG_NAME, "finishing")
    val FAILED = Tag(STATUS_TAG_NAME, "failed")

    val AUTO_SCALING_GROUP = "autoScalingGroup"
  }


  def tagAutoScalingGroup(as: AutoScaling, groupName: String, status: String) {
    as.createTags(groupName, InstanceTags.PRODUCT_TAG)
    as.createTags(groupName, Tag(InstanceTags.AUTO_SCALING_GROUP, groupName))
    as.createTags(groupName, Tag(InstanceTags.STATUS_TAG_NAME, status))
    as.createTags(groupName, Tag("Name", groupName))
  }

  /* Some file and pretty printing utils */
  def writeStringToFile(s: String, file: File) {
    val writer = new PrintWriter(file)
    writer.print(s)
    writer.close()
  }

  def listRecursively(f: File): Seq[File] = {
    if (f.exists) {
      f.listFiles.filter(_.isDirectory).flatMap(listRecursively) ++
      f.listFiles
    } else Seq()
  }

  def deleteRecursively(file: File) = {
    listRecursively(file).foreach{ f =>
      if (!f.delete) throw new RuntimeException("Failed to delete " + f.getAbsolutePath)
    }
  }

  def printInterval(intervalSecs: Long): String = {
    (intervalSecs / 60) + " min " + (intervalSecs % 60) + " sec"
  }

  @scala.annotation.tailrec
  def waitForResource[R](getResource: => Option[R], tries: Int, timeStep: Time) : Option[R] = {
    val resource = getResource

    if (resource.isEmpty && tries <= 0) {
      Thread.sleep(timeStep.inSeconds * 1000)
      waitForResource(getResource, tries - 1, timeStep)
    } else resource
  }

}
