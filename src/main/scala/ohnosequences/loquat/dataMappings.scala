package ohnosequences.loquat

import ohnosequences.datasets._
// import ohnosequences.cosas.ops.typeSets._
import ohnosequences.cosas._, records._, fns._, types._, klists._
import ohnosequences.awstools.s3._
import better.files._
import upickle.Js


case class ProcessingResult(id: String, message: String)

trait AnyDataMapping {

  val id: String

  type DataProcessing <: AnyDataProcessingBundle
  val  dataProcessing: DataProcessing

  /* These are records with references to the remote locations of
     where to get inputs and where to put outputs of the dataMapping */
  type RemoteInput = DataProcessing#Input#Raw with AnyKList.withBound[AnyDenotation { type Value = S3DataLocation }]
  val  remoteInput: RemoteInput

  type RemoteOutput = DataProcessing#Output#Raw with AnyKList.withBound[AnyDenotation { type Value = S3DataLocation }]
  val  remoteOutput: RemoteOutput

  /* These two vals a needed for serialization */
  // should be provided implicitly:
  // val inputsToMap:  ToMap[RemoteInput,  AnyData, S3DataLocation]
  // val outputsToMap: ToMap[RemoteOutput, AnyData, S3DataLocation]
  def inputsToMap: Map[String,AnyS3Address] =
    (dataProcessing.input.keys.types.asList map { t => t.label }) zip (remoteInput.asList map { d => d.value.location }) toMap

  def outputsToMap: Map[String,AnyS3Address] =
    (dataProcessing.output.keys.types.asList map { t => t.label }) zip (remoteOutput.asList map { d => d.value.location }) toMap
  /* We can transform any dataMapping to this simple form (but not another way round) */
  private[loquat]
  def simplify: SimpleDataMapping =
    SimpleDataMapping(
      id = this.id,
      inputs = inputsToMap,
      outputs = outputsToMap
    )
}

case class DataMapping[
  DP <: AnyDataProcessingBundle
](
  val id: String,
  val dataProcessing: DP
)(
  val remoteInput: DP#Input#Raw with AnyKList.withBound[AnyDenotation { type Value = S3DataLocation }],
  val remoteOutput: DP#Output#Raw with AnyKList.withBound[AnyDenotation { type Value = S3DataLocation }]
)
extends AnyDataMapping {

  type DataProcessing = DP
}


/* This is easy to parse/serialize, but it's only for internal use. */
private[loquat]
case class SimpleDataMapping(
  val id: String,
  val inputs: Map[String, AnyS3Address],
  val outputs: Map[String, AnyS3Address]
)
