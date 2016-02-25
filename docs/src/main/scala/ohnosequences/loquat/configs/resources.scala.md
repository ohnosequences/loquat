
```scala
package ohnosequences.loquat
```

Configuration of resources

```scala
private[loquat]
case class ResourceNames(prefix: String, bucketName: String) {
```

name of queue with dataMappings

```scala
  val inputQueue: String = prefix + "-loquat-input"
```

name of topic for dataMappings result notifications

```scala
  val outputQueue: String = prefix + "-loquat-output"
```

name of queue with errors (will be subscribed to errorTopic)

```scala
  val errorQueue: String = prefix + "-loquat-errors"
```

name of bucket for logs files

```scala
  val bucket: String = bucketName
```

topic name to notificate user about termination of loquat

```scala
  val notificationTopic: String = prefix + "-loquat-notifications"
```

name of the manager autoscaling group

```scala
  val managerGroup: String = prefix + "-loquat-manager"
```

name of the workers autoscaling group

```scala
  val workersGroup: String = prefix + "-loquat-workers"
}

```




[main/scala/ohnosequences/loquat/configs/autoscaling.scala]: autoscaling.scala.md
[main/scala/ohnosequences/loquat/configs/general.scala]: general.scala.md
[main/scala/ohnosequences/loquat/configs/loquat.scala]: loquat.scala.md
[main/scala/ohnosequences/loquat/configs/resources.scala]: resources.scala.md
[main/scala/ohnosequences/loquat/configs/termination.scala]: termination.scala.md
[main/scala/ohnosequences/loquat/configs/user.scala]: user.scala.md
[main/scala/ohnosequences/loquat/dataMappings.scala]: ../dataMappings.scala.md
[main/scala/ohnosequences/loquat/dataProcessing.scala]: ../dataProcessing.scala.md
[main/scala/ohnosequences/loquat/logger.scala]: ../logger.scala.md
[main/scala/ohnosequences/loquat/loquats.scala]: ../loquats.scala.md
[main/scala/ohnosequences/loquat/manager.scala]: ../manager.scala.md
[main/scala/ohnosequences/loquat/terminator.scala]: ../terminator.scala.md
[main/scala/ohnosequences/loquat/utils.scala]: ../utils.scala.md
[main/scala/ohnosequences/loquat/worker.scala]: ../worker.scala.md
[test/scala/ohnosequences/loquat/test/config.scala]: ../../../../../test/scala/ohnosequences/loquat/test/config.scala.md
[test/scala/ohnosequences/loquat/test/data.scala]: ../../../../../test/scala/ohnosequences/loquat/test/data.scala.md
[test/scala/ohnosequences/loquat/test/dataMappings.scala]: ../../../../../test/scala/ohnosequences/loquat/test/dataMappings.scala.md
[test/scala/ohnosequences/loquat/test/dataProcessing.scala]: ../../../../../test/scala/ohnosequences/loquat/test/dataProcessing.scala.md
[test/scala/ohnosequences/loquat/test/md5.scala]: ../../../../../test/scala/ohnosequences/loquat/test/md5.scala.md