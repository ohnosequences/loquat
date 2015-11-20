package ohnosequences.loquat

import ohnosequences.datasets._, dataSets._, s3Locations._
import ohnosequences.cosas.ops.typeSets._

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
  type RemoteInput = DataProcessing#Input#LocationsAt[S3DataLocation]
  val  remoteInput: RemoteInput

  type RemoteOutput = DataProcessing#Output#LocationsAt[S3DataLocation]
  val  remoteOutput: RemoteOutput

  /* These two vals a needed for serialization */
  // should be provided implicitly:
  val inputsToMap:  ToMap[RemoteInput,  AnyData, S3DataLocation]
  val outputsToMap: ToMap[RemoteOutput, AnyData, S3DataLocation]

  /* We can transform any dataMapping to this simple form (but not another way round) */
  private[loquat]
  def simplify: SimpleDataMapping =
    SimpleDataMapping(
      id = this.id,
      inputs = this.inputsToMap(this.remoteInput)
        .map{ case (data, s3loc) => (data.label -> s3loc.location) },
      outputs = this.outputsToMap(this.remoteOutput)
        .map{ case (data, s3loc) => (data.label -> s3loc.location) }
    )
}

case class DataMapping[
  DP <: AnyDataProcessingBundle
](val id: String,
  val dataProcessing: DP
)(val remoteInput: DP#Input#LocationsAt[S3DataLocation],
  val remoteOutput: DP#Output#LocationsAt[S3DataLocation]
)(implicit
  val inputsToMap:  ToMap[DP#Input#LocationsAt[S3DataLocation], AnyData, S3DataLocation],
  val outputsToMap: ToMap[DP#Output#LocationsAt[S3DataLocation], AnyData, S3DataLocation]
) extends AnyDataMapping {

  type DataProcessing = DP
}


/* This is easy to parse/serialize, but it's only for internal use. */
private[loquat]
case class SimpleDataMapping(
  val id: String,
  val inputs: Map[String, AnyS3Address],
  val outputs: Map[String, AnyS3Address]
)
