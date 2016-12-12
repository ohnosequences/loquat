
```scala
package ohnosequences.loquat

import com.typesafe.scalalogging.LazyLogging
```

Any config here can validate itself (in runtime)

```scala
trait AnyConfig extends LazyLogging {

  val configLabel: String

  def validationErrors(aws: AWSClients): Seq[String]
```

Config dependencies

```scala
  val subConfigs: Seq[AnyConfig]
```

This method validates subconfigs and logs validation errors

```scala
  final def validateWithLogging(aws: AWSClients): Seq[String] = {
    val subErrors = subConfigs.flatMap{ _.validateWithLogging(aws) }

    val errors = validationErrors(aws)
    errors.foreach{ msg => logger.error(msg) }

    if (errors.isEmpty) logger.debug(s"Validated ${configLabel}")

    subErrors ++ errors
  }
}

abstract class Config(val configLabel: String)(val subConfigs: AnyConfig*) extends AnyConfig

```




[main/scala/ohnosequences/loquat/configs/autoscaling.scala]: autoscaling.scala.md
[main/scala/ohnosequences/loquat/configs/awsClients.scala]: awsClients.scala.md
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