
```scala
package ohnosequences.loquat

import java.io.{PrintWriter, File}
import ohnosequences.awstools.ec2.Tag
import ohnosequences.awstools.autoscaling.AutoScaling


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

object utils {

  def tagAutoScalingGroup(as: AutoScaling, groupName: String, status: String) {
    as.createTags(groupName, InstanceTags.PRODUCT_TAG)
    as.createTags(groupName, Tag(InstanceTags.AUTO_SCALING_GROUP, groupName))
    as.createTags(groupName, Tag(InstanceTags.STATUS_TAG_NAME, status))
    as.createTags(groupName, Tag("Name", groupName))
  }
```

Some file and pretty printing utils

```scala
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

```




[main/scala/ohnosequences/nisperito/bundles/InstructionsBundle.scala]: bundles/InstructionsBundle.scala.md
[main/scala/ohnosequences/nisperito/bundles/LogUploaderBundle.scala]: bundles/LogUploaderBundle.scala.md
[main/scala/ohnosequences/nisperito/bundles/ManagerBundle.scala]: bundles/ManagerBundle.scala.md
[main/scala/ohnosequences/nisperito/bundles/TerminationDaemonBundle.scala]: bundles/TerminationDaemonBundle.scala.md
[main/scala/ohnosequences/nisperito/bundles/WorkerBundle.scala]: bundles/WorkerBundle.scala.md
[main/scala/ohnosequences/nisperito/Config.scala]: Config.scala.md
[main/scala/ohnosequences/nisperito/dataMappings.scala]: dataMappings.scala.md
[main/scala/ohnosequences/nisperito/Nisperito.scala]: Nisperito.scala.md
[main/scala/ohnosequences/nisperito/Utils.scala]: Utils.scala.md
[test/scala/ohnosequences/nisperito/dataMappings.scala]: ../../../../test/scala/ohnosequences/nisperito/dataMappings.scala.md
[test/scala/ohnosequences/nisperito/instructions.scala]: ../../../../test/scala/ohnosequences/nisperito/instructions.scala.md