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

  /* These are records with references to the remote locations of
     where to get inputs and where to put outputs of the dataMapping */
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

/* This is easy to parse/serialize, but it's only for internal use. */
private[loquat] case class SimpleDataMapping(
  val id: String,
  val inputs: Map[String, AnyRemoteResource],
  val outputs: Map[String, S3Resource]
)
