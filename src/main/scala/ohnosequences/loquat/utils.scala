package ohnosequences.loquat

import ohnosequences.datasets._
import ohnosequences.cosas._, types._, klists._

import com.typesafe.scalalogging.LazyLogging

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import ohnosequences.awstools._, ec2._, regions._, autoscaling._
import ohnosequences.statika
import better.files._
import scala.collection.JavaConversions._
import scala.util._
import scala.concurrent.duration._
import java.util.concurrent._
import java.nio.file._
import java.io.File

case object utils {

  type ResourcesSet[D <: AnyDataSet, R <: AnyDataResource] =
    D#Keys#Raw { type Bound = AnyDenotation { type Value <: R } }
    // with AnyKList.withBound[AnyDenotation { type Value <: R }]


  // def toMap[V <: AnyDataResource](l: AnyKList.Of[AnyDenotation { type Value <: V }]): Map[String, V] =
  //   l.asList.map{ d => (d.tpe.label, d.value) }.toMap

  def toMap[V <: AnyDataResource](l: Map[AnyData, V]): Map[String, V] =
    l.map{ case (d, v) => (d.tpe.label, v) }

  def localTargetTmpDir(): File =
    Files.createTempDirectory(Paths.get("target/"), "loquat.").toFile

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

  implicit def resultToTry[T](r: statika.Result[T]): Try[T] = r match {
    case statika.Success(_, s) => util.Success(s)
    case statika.Failure(e) => util.Failure(new RuntimeException(e.mkString("\n")))
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

}
