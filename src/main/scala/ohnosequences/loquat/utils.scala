package ohnosequences.loquat

import ohnosequences.datasets._
import ohnosequences.cosas._, types._, klists._

import com.typesafe.scalalogging.LazyLogging

import com.amazonaws.auth.InstanceProfileCredentialsProvider
import ohnosequences.awstools.ec2._
import ohnosequences.awstools.autoscaling.{ AutoScaling, AutoScalingGroup }

import better.files._
import scala.collection.JavaConversions._
import scala.util._
import scala.concurrent.duration._
import java.util.concurrent._


case object utils {

  type ResourcesSet[D <: AnyDataSet, R <: AnyDataResource] =
    D#Keys#Raw { type Bound = AnyDenotation { type Value <: R } }
    // with AnyKList.withBound[AnyDenotation { type Value <: R }]


  // def toMap[V <: AnyDataResource](l: AnyKList.Of[AnyDenotation { type Value <: V }]): Map[String, V] =
  //   l.asList.map{ d => (d.tpe.label, d.value) }.toMap

  def toMap[V <: AnyDataResource](l: Map[AnyData, V]): Map[String, V] =
    l.map{ case (d, v) => (d.tpe.label, v) }


  def instanceAWSClients(config: AnyLoquatConfig) = AWSClients(
    InstanceProfileCredentialsProvider.getInstance(),
    config.region
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


  sealed trait AnyStatusTag {
    val status: String
    val instanceTag = InstanceTag(StatusTag.label, status)
  }
  implicit def toAwsInstanceTag(st: AnyStatusTag): InstanceTag = st.instanceTag

  class StatusTag(val status: String) extends AnyStatusTag

  case object StatusTag {
    val label: String = "status"

    case object preparing   extends StatusTag("preparing")
    case object running     extends StatusTag("running")

    case object processing  extends StatusTag("processing")
    case object idle        extends StatusTag("idle")
    case object terminating extends StatusTag("terminating")
    // case object failed      extends StatusTag("failed")
  }

  def tagAutoScalingGroup(as: AutoScaling, group: AutoScalingGroup, statusTag: AnyStatusTag): Unit = {
    as.createTags(group.name, InstanceTag("product", "loquat"))
    // TODO: loquat name/id
    as.createTags(group.name, InstanceTag("group", group.name))
    as.createTags(group.name, statusTag)
  }


  @scala.annotation.tailrec
  def waitForResource[R](getResource: => Option[R], tries: Int, timeStep: FiniteDuration) : Option[R] = {
    val resource = getResource

    if (resource.isEmpty && tries <= 0) {
      Thread.sleep(timeStep.toMillis)
      waitForResource(getResource, tries - 1, timeStep)
    } else resource
  }

}
