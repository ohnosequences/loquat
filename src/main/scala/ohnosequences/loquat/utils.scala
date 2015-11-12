package ohnosequences.loquat

case object utils {

  import java.io.{PrintWriter, File}
  import ohnosequences.awstools.ec2._
  import ohnosequences.awstools.autoscaling.{ AutoScaling, AutoScalingGroup }
  // import com.amazonaws.services.autoscaling.model._
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

  /* Some file and pretty printing utils */
  def writeStringToFile(s: String, file: File): Unit = {
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

  @scala.annotation.tailrec
  def waitForResource[R](getResource: => Option[R], tries: Int, timeStep: Time) : Option[R] = {
    val resource = getResource

    if (resource.isEmpty && tries <= 0) {
      Thread.sleep(timeStep.inSeconds * 1000)
      waitForResource(getResource, tries - 1, timeStep)
    } else resource
  }


  /* File utils */
  // type File = java.io.File

  // def file(name: String): FileOps = FileOps(new File(name))

  case class file(javaFile: File) extends AnyVal {

    def /(suffix: String): file = file(new File(javaFile, suffix))

    def parent: file = file(javaFile.getParent)

    def name: String = javaFile.getName
    def path: String = javaFile.getCanonicalPath

    def rename(change: String => String): file = parent / change(name)
  }

  object file {

    def apply(name: String): file = file(new File(name))
  }

  implicit def fromJavaFile(f: File): file = file(f)
  implicit def   toJavaFile(f: file): File = f.javaFile
  implicit def fileToString(f: file): String = f.path

}
