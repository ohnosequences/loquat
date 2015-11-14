
```scala
package ohnosequences.loquat

case object dataMappings {

  import dataProcessing._

  import ohnosequences.datasets._, dataSets._, s3Locations._
  import ohnosequences.cosas.ops.typeSets._

  import ohnosequences.awstools.s3._
  import better.files._
  import upickle.Js


  case class ProcessingResult(id: String, message: String)

  type RemotesFor[DS <: AnyDataSet] = DS#LocationsAt[S3DataLocation]

  trait AnyDataMapping {

    val id: String

    type DataProcessing <: AnyDataProcessingBundle
    val  dataProcessing: DataProcessing
```

These are records with references to the remote locations of
where to get inputs and where to put outputs of the dataMapping

```scala
    type RemoteInput = RemotesFor[DataProcessing#Input]
    val  remoteInput: RemoteInput

    type RemoteOutput = RemotesFor[DataProcessing#Output]
    val  remoteOutput: RemoteOutput
```

These two vals a needed for serialization

```scala
    // should be provided implicitly:
    val inputsToMap:  ToMap[RemoteInput,  AnyData, S3DataLocation]
    val outputsToMap: ToMap[RemoteOutput, AnyData, S3DataLocation]
  }

  case class DataMapping[
    DP <: AnyDataProcessingBundle
  ](val id: String,
    val dataProcessing: DP
  )(val remoteInput: RemotesFor[DP#Input],
    val remoteOutput: RemotesFor[DP#Output]
  )(implicit
    val inputsToMap:  ToMap[RemotesFor[DP#Input], AnyData, S3DataLocation],
    val outputsToMap: ToMap[RemotesFor[DP#Output], AnyData, S3DataLocation]
  ) extends AnyDataMapping {

    type DataProcessing = DP
  }
```

This is easy to parse/serialize, but it's only for internal use.

```scala
  protected[loquat]
    case class SimpleDataMapping(
      val id: String,
      val inputs: Map[String, AnyS3Address],
      val outputs: Map[String, AnyS3Address]
    )
```

and we can transform any dataMapping to this simple form (but not another way round)

```scala
  protected[loquat]
    def simplify(dataMapping: AnyDataMapping): SimpleDataMapping =
      SimpleDataMapping(
        id = dataMapping.id,
        inputs = dataMapping.inputsToMap(dataMapping.remoteInput)
          .map{ case (data, s3loc) => (data.label -> s3loc.location) },
        outputs = dataMapping.outputsToMap(dataMapping.remoteOutput)
          .map{ case (data, s3loc) => (data.label -> s3loc.location) }
      )

}

```




[test/scala/ohnosequences/loquat/dataMappings.scala]: ../../../../test/scala/ohnosequences/loquat/dataMappings.scala.md
[test/scala/ohnosequences/loquat/instructions.scala]: ../../../../test/scala/ohnosequences/loquat/instructions.scala.md
[main/scala/ohnosequences/loquat/dataProcessing.scala]: dataProcessing.scala.md
[main/scala/ohnosequences/loquat/workers.scala]: workers.scala.md
[main/scala/ohnosequences/loquat/managers.scala]: managers.scala.md
[main/scala/ohnosequences/loquat/daemons.scala]: daemons.scala.md
[main/scala/ohnosequences/loquat/loquats.scala]: loquats.scala.md
[main/scala/ohnosequences/loquat/utils.scala]: utils.scala.md
[main/scala/ohnosequences/loquat/dataMappings.scala]: dataMappings.scala.md
[main/scala/ohnosequences/loquat/configs.scala]: configs.scala.md