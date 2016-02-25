
```scala
package ohnosequences.loquat.test

import ohnosequences.datasets._
import ohnosequences.cosas._, klists._, types._, records._

case object data {

  // inputs:
  case object prefix extends Data("prefix")
  case object text extends Data("text")
  case object matrix extends FileData("matrix")("txt")

  // outputs:
  case object transposed extends FileData("transposed")("txt")

}

```




[main/scala/ohnosequences/loquat/configs/autoscaling.scala]: ../../../../../main/scala/ohnosequences/loquat/configs/autoscaling.scala.md
[main/scala/ohnosequences/loquat/configs/general.scala]: ../../../../../main/scala/ohnosequences/loquat/configs/general.scala.md
[main/scala/ohnosequences/loquat/configs/loquat.scala]: ../../../../../main/scala/ohnosequences/loquat/configs/loquat.scala.md
[main/scala/ohnosequences/loquat/configs/resources.scala]: ../../../../../main/scala/ohnosequences/loquat/configs/resources.scala.md
[main/scala/ohnosequences/loquat/configs/termination.scala]: ../../../../../main/scala/ohnosequences/loquat/configs/termination.scala.md
[main/scala/ohnosequences/loquat/configs/user.scala]: ../../../../../main/scala/ohnosequences/loquat/configs/user.scala.md
[main/scala/ohnosequences/loquat/dataMappings.scala]: ../../../../../main/scala/ohnosequences/loquat/dataMappings.scala.md
[main/scala/ohnosequences/loquat/dataProcessing.scala]: ../../../../../main/scala/ohnosequences/loquat/dataProcessing.scala.md
[main/scala/ohnosequences/loquat/logger.scala]: ../../../../../main/scala/ohnosequences/loquat/logger.scala.md
[main/scala/ohnosequences/loquat/loquats.scala]: ../../../../../main/scala/ohnosequences/loquat/loquats.scala.md
[main/scala/ohnosequences/loquat/manager.scala]: ../../../../../main/scala/ohnosequences/loquat/manager.scala.md
[main/scala/ohnosequences/loquat/terminator.scala]: ../../../../../main/scala/ohnosequences/loquat/terminator.scala.md
[main/scala/ohnosequences/loquat/utils.scala]: ../../../../../main/scala/ohnosequences/loquat/utils.scala.md
[main/scala/ohnosequences/loquat/worker.scala]: ../../../../../main/scala/ohnosequences/loquat/worker.scala.md
[test/scala/ohnosequences/loquat/test/config.scala]: config.scala.md
[test/scala/ohnosequences/loquat/test/data.scala]: data.scala.md
[test/scala/ohnosequences/loquat/test/dataMappings.scala]: dataMappings.scala.md
[test/scala/ohnosequences/loquat/test/dataProcessing.scala]: dataProcessing.scala.md
[test/scala/ohnosequences/loquat/test/md5.scala]: md5.scala.md