
```scala
package ohnosequences.loquat

import utils._

import ohnosequences.datasets._

import ohnosequences.cosas._, records._, fns._, types._, klists._
import ohnosequences.awstools.s3._

import better.files._
import upickle.Js


trait AnyDataMapping {

  type DataProcessing <: AnyDataProcessingBundle
  val  dataProcessing: DataProcessing
```

These are records with references to the remote locations of
where to get inputs and where to put outputs of the dataMapping

```scala
  type RemoteInput = ResourcesSet[DataProcessing#Input, AnyRemoteResource]
  val  remoteInput: RemoteInput

  type RemoteOutput = ResourcesSet[DataProcessing#Output, S3Resource]
  val  remoteOutput: RemoteOutput

}

case class DataMapping[
  DP <: AnyDataProcessingBundle
](val dataProcessing: DP)(
  val remoteInput: ResourcesSet[DP#Input, AnyRemoteResource],
  val remoteOutput: ResourcesSet[DP#Output, S3Resource]
) extends AnyDataMapping {

  type DataProcessing = DP
}


case class ProcessingResult(id: String, message: String)
```

This is easy to parse/serialize, but it's only for internal use.

```scala
private[loquat] case class SimpleDataMapping(
  val id: String,
  val inputs: Map[String, AnyRemoteResource],
  val outputs: Map[String, S3Resource]
)

```




[test/scala/ohnosequences/loquat/test/dataProcessing.scala]: ../../../../test/scala/ohnosequences/loquat/test/dataProcessing.scala.md
[test/scala/ohnosequences/loquat/test/md5.scala]: ../../../../test/scala/ohnosequences/loquat/test/md5.scala.md
[test/scala/ohnosequences/loquat/test/dataMappings.scala]: ../../../../test/scala/ohnosequences/loquat/test/dataMappings.scala.md
[test/scala/ohnosequences/loquat/test/data.scala]: ../../../../test/scala/ohnosequences/loquat/test/data.scala.md
[test/scala/ohnosequences/loquat/test/config.scala]: ../../../../test/scala/ohnosequences/loquat/test/config.scala.md
[main/scala/ohnosequences/loquat/dataProcessing.scala]: dataProcessing.scala.md
[main/scala/ohnosequences/loquat/terminator.scala]: terminator.scala.md
[main/scala/ohnosequences/loquat/configs/user.scala]: configs/user.scala.md
[main/scala/ohnosequences/loquat/configs/resources.scala]: configs/resources.scala.md
[main/scala/ohnosequences/loquat/configs/general.scala]: configs/general.scala.md
[main/scala/ohnosequences/loquat/configs/autoscaling.scala]: configs/autoscaling.scala.md
[main/scala/ohnosequences/loquat/configs/termination.scala]: configs/termination.scala.md
[main/scala/ohnosequences/loquat/configs/loquat.scala]: configs/loquat.scala.md
[main/scala/ohnosequences/loquat/loquats.scala]: loquats.scala.md
[main/scala/ohnosequences/loquat/utils.scala]: utils.scala.md
[main/scala/ohnosequences/loquat/dataMappings.scala]: dataMappings.scala.md
[main/scala/ohnosequences/loquat/worker.scala]: worker.scala.md
[main/scala/ohnosequences/loquat/logger.scala]: logger.scala.md
[main/scala/ohnosequences/loquat/manager.scala]: manager.scala.md