package ohnosequences.loquat

case object utils {

  import java.io.{PrintWriter, File}
  import ohnosequences.awstools.ec2.Tag
  import ohnosequences.awstools.autoscaling.{ AutoScaling, AutoScalingGroup }
  import com.amazonaws.services.autoscaling.model._
  import com.typesafe.scalalogging.LazyLogging
  import scala.util._
  import scala.concurrent.duration._
  import java.util.concurrent._


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

  // A minimal wrapped around the Java scheduling thing
  // Note, that this ScheduledFuture has cancel(Boolean) method
  def schedule(
    after: FiniteDuration,
    every: FiniteDuration
  )(block: => Unit): ScheduledFuture[_] = {

    new ScheduledThreadPoolExecutor(1).scheduleAtFixedRate(
      new Runnable { def run(): Unit = block },
      after.toSeconds,
      every.toSeconds,
      SECONDS
    )
  }


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


  def tagAutoScalingGroup(as: AutoScaling, groupName: String, status: String): Unit = {
    as.createTags(groupName, InstanceTags.PRODUCT_TAG)
    as.createTags(groupName, Tag(InstanceTags.AUTO_SCALING_GROUP, groupName))
    as.createTags(groupName, Tag(InstanceTags.STATUS_TAG_NAME, status))
    as.createTags(groupName, Tag("Name", groupName))
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

  // FIXME: use futures here:
  @scala.annotation.tailrec
  def waitForResource[R](getResource: => Option[R], tries: Int, timeStep: FiniteDuration) : Option[R] = {
    val resource = getResource

    if (resource.isEmpty && tries <= 0) {
      Thread.sleep(timeStep.toMillis)
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
