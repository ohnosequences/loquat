package ohnosequences.nispero

import java.io.{PrintWriter, File}
import ohnosequences.awstools.ec2.Tag
import ohnosequences.awstools.autoscaling.AutoScaling


object InstanceTags {
  val PRODUCT_TAG = Tag("product", "nispero")

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

object utils {

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

  def printInterval(intervalSecs: Long): String = {
    (intervalSecs / 60) + " min " + (intervalSecs % 60) + " sec"
  }

  def waitForResource[A](resource: => Option[A]) : Option[A] = {
    var iteration = 1
    var current: Option[A] = None
    val limit = 50

    do {
      current = resource
      iteration += 1
      Thread.sleep(1000)
    } while (current.isEmpty && iteration < limit)

    current
  }

}
