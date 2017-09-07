package ohnosequences.loquat

import ohnosequences.datasets._
import ohnosequences.cosas._, types._, klists._

import com.typesafe.scalalogging.LazyLogging

import com.amazonaws.auth.InstanceProfileCredentialsProvider
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import ohnosequences.awstools._, ec2._, regions._, autoscaling._
import scala.collection.JavaConverters._
import scala.util._
import scala.concurrent.duration._
import java.util.concurrent._
import java.io.File
import java.nio.file.{ Files, Path, Paths }
import java.nio.charset.Charset

case object utils {

  type ResourcesSet[D <: AnyDataSet, R <: AnyDataResource] =
    D#Keys#Raw { type Bound = AnyDenotation { type Value <: R } }
    // with AnyKList.withBound[AnyDenotation { type Value <: R }]


  // def toMap[V <: AnyDataResource](l: AnyKList.Of[AnyDenotation { type Value <: V }]): Map[String, V] =
  //   l.asList.map{ d => (d.tpe.label, d.value) }.toMap

  def toMap[V <: AnyDataResource](l: Map[AnyData, V]): Map[String, V] =
    l.map{ case (d, v) => (d.tpe.label, v) }


  def instanceAWSClients(config: AnyLoquatConfig) = AWSClients(
    config.region,
    InstanceProfileCredentialsProvider.getInstance()
  )

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

  // A minimal wrapper around the Java scheduling thing
  case class Scheduler(val threadsNumber: Int) {
    lazy final val pool = new ScheduledThreadPoolExecutor(threadsNumber)

    // Note, that the returned ScheduledFuture has cancel(Boolean) method
    def repeat(
      after: FiniteDuration,
      every: FiniteDuration
    )(block: => Unit): ScheduledFuture[_] = {

      pool.scheduleAtFixedRate(
        new Runnable { def run(): Unit = block },
        after.toSeconds,
        every.toSeconds,
        SECONDS
      )
    }
  }


  sealed class StatusTag(val status: String)

  case object StatusTag {
    val label: String = "status"

    case object preparing   extends StatusTag("preparing")
    case object running     extends StatusTag("running")

    case object processing  extends StatusTag("processing")
    case object idle        extends StatusTag("idle")
    case object terminating extends StatusTag("terminating")
    // case object failed      extends StatusTag("failed")
  }

  private[loquat] case object FileUtils {
    def file(path: String): File = new File(path)

    implicit def pathToFile(path: Path): File = path.toFile()

    implicit class FileOps(val file: File) extends AnyVal {
      def path: Path = file.toPath()

      def /(suffix: String): File =
        new File(file, suffix)

      /** Creates directory with all parents */
      def createDirectory: File = {
        if (file.exists()) file
        else Files.createDirectories( path )
      }

      /** Creates a file if it doesn't exist with all parent directories */
      def createFile: File = {
        if (file.exists()) file
        else {
          Files.createDirectories( path.getParent() )
          Files.createFile( path )
        }
      }

      def overwrite(text: String): File =
        Files.write(path, text.getBytes(Charset.defaultCharset))

      def deleteRecursively(): Unit = {
        if (file.isDirectory) {
          file.listFiles.foreach { _.deleteRecursively() }
        }
        file.delete()
      }

      def lines: Iterator[String] =
        Files.lines(path).iterator.asScala
    }
  }
}
