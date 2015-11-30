package ohnosequences.loquat

import utils._

import ohnosequences.datasets._

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
  type RemoteInput = DataSetLocations[DataProcessing#Input, S3DataLocation]
  val  remoteInput: RemoteInput

  type RemoteOutput = DataSetLocations[DataProcessing#Output, S3DataLocation]
  val  remoteOutput: RemoteOutput

  /* These two vals are needed for serialization */
  def inputsMap: Map[String, AnyS3Address] =
    dataProcessing.input.keys.types.asList.map{ t => t.label } zip
    remoteInput.asList.map{ d => d.value.location } toMap

  def outputsMap: Map[String, AnyS3Address] =
    dataProcessing.output.keys.types.asList.map{ t => t.label } zip
    remoteOutput.asList.map{ d => d.value.location } toMap

  /* We can transform any dataMapping to this simple form (but not another way round) */
  private[loquat]
  def simplify: SimpleDataMapping =
    SimpleDataMapping(
      id = this.id,
      inputs = inputsMap,
      outputs = outputsMap
    )
}

case class DataMapping[
  DP <: AnyDataProcessingBundle
](val id: String,
  val dataProcessing: DP
)(val remoteInput: DataSetLocations[DP#Input, S3DataLocation],
  val remoteOutput: DataSetLocations[DP#Output, S3DataLocation]
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
