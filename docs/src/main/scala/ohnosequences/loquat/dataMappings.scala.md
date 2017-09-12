
```scala
package ohnosequences.loquat

import utils._

import ohnosequences.datasets._

import ohnosequences.cosas._, records._, fns._, types._, klists._
import ohnosequences.awstools.s3._

import better.files._
import upickle.Js


trait AnyDataMapping { dataMapping =>

  // This label is just to distinguish data mappings, it is not an ID
  val label: String

  type DataProcessing <: AnyDataProcessingBundle
  val  dataProcessing: DataProcessing


  type RemoteInput = Map[AnyData, AnyRemoteResource]
  val  remoteInput: RemoteInput

  type RemoteOutput = Map[AnyData, S3Resource]
  val  remoteOutput: RemoteOutput

  def checkDataKeys: Seq[String] = {

    def keyLabels(rec: AnyRecordType): Set[String] =
      rec.keys.types.asList.toSet.map { tpe: AnyType => tpe.label }

    val mapInputKeys = remoteInput.keySet.map{ _.label }
    val dataInputKeys = keyLabels(dataProcessing.input)

    val missingInputKeys: Set[String] = dataInputKeys diff mapInputKeys
    val extraInputKeys:   Set[String] = mapInputKeys diff dataInputKeys


    val mapOutputKeys = remoteOutput.keySet.map{ _.label }
    val dataOutputKeys = keyLabels(dataProcessing.output)

    val missingOutputKeys: Set[String] = dataOutputKeys diff mapOutputKeys
    val extraOutputKeys:   Set[String] = mapOutputKeys diff dataOutputKeys

    missingInputKeys.toSeq.map { key =>
      s"The [${key}] input key is missing in the remoteInput of [${dataMapping.label}]"
    } ++
    extraInputKeys.toSeq.map { key =>
      s"The [${key}] key doesn't exist in the [${dataMapping.label}] input dataset"
    } ++
    missingOutputKeys.toSeq.map { key =>
      s"The [${key}] output key is missing in the remoteOutput of [${dataMapping.label}]"
    } ++
    extraOutputKeys.toSeq.map { key =>
      s"The [${key}] key doesn't exist in the [${dataMapping.label}] output dataset"
    }
  }
}

case class DataMapping[DP <: AnyDataProcessingBundle](
  val label: String,
  val dataProcessing: DP
)(val remoteInput:  Map[AnyData, AnyRemoteResource],
  val remoteOutput: Map[AnyData, S3Resource]
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




[main/scala/ohnosequences/loquat/configs/autoscaling.scala]: configs/autoscaling.scala.md
[main/scala/ohnosequences/loquat/configs/awsClients.scala]: configs/awsClients.scala.md
[main/scala/ohnosequences/loquat/configs/general.scala]: configs/general.scala.md
[main/scala/ohnosequences/loquat/configs/loquat.scala]: configs/loquat.scala.md
[main/scala/ohnosequences/loquat/configs/resources.scala]: configs/resources.scala.md
[main/scala/ohnosequences/loquat/configs/termination.scala]: configs/termination.scala.md
[main/scala/ohnosequences/loquat/configs/user.scala]: configs/user.scala.md
[main/scala/ohnosequences/loquat/dataMappings.scala]: dataMappings.scala.md
[main/scala/ohnosequences/loquat/dataProcessing.scala]: dataProcessing.scala.md
[main/scala/ohnosequences/loquat/logger.scala]: logger.scala.md
[main/scala/ohnosequences/loquat/loquats.scala]: loquats.scala.md
[main/scala/ohnosequences/loquat/manager.scala]: manager.scala.md
[main/scala/ohnosequences/loquat/terminator.scala]: terminator.scala.md
[main/scala/ohnosequences/loquat/utils.scala]: utils.scala.md
[main/scala/ohnosequences/loquat/worker.scala]: worker.scala.md
[test/scala/ohnosequences/loquat/test/config.scala]: ../../../../test/scala/ohnosequences/loquat/test/config.scala.md
[test/scala/ohnosequences/loquat/test/data.scala]: ../../../../test/scala/ohnosequences/loquat/test/data.scala.md
[test/scala/ohnosequences/loquat/test/dataMappings.scala]: ../../../../test/scala/ohnosequences/loquat/test/dataMappings.scala.md
[test/scala/ohnosequences/loquat/test/dataProcessing.scala]: ../../../../test/scala/ohnosequences/loquat/test/dataProcessing.scala.md
[test/scala/ohnosequences/loquat/test/md5.scala]: ../../../../test/scala/ohnosequences/loquat/test/md5.scala.md