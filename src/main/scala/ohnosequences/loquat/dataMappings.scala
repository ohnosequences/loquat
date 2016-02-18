package ohnosequences.loquat

import utils._

import ohnosequences.datasets._

import ohnosequences.cosas._, records._, fns._, types._, klists._
import ohnosequences.awstools.s3._

import better.files._
import upickle.Js


trait AnyDataMapping {

  // This label is just to distinguish data mappings, it is not an ID
  val label: String

  type DataProcessing <: AnyDataProcessingBundle
  val  dataProcessing: DataProcessing


  type RemoteInput = Map[AnyData, AnyRemoteResource]
  val  remoteInput: RemoteInput

  type RemoteOutput = Map[AnyData, S3Resource]
  val  remoteOutput: RemoteOutput
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

/* This is easy to parse/serialize, but it's only for internal use. */
private[loquat] case class SimpleDataMapping(
  val id: String,
  val inputs: Map[String, AnyRemoteResource],
  val outputs: Map[String, S3Resource]
)
