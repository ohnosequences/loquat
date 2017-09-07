package ohnosequences.loquat

import utils._
import ohnosequences.datasets._
import ohnosequences.cosas._, records._, fns._, types._, klists._
import ohnosequences.awstools.s3._

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream }
import java.util.Base64
import java.nio.charset.StandardCharsets.UTF_8

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

/* This is easy to parse/serialize, but it's only for internal use. */
private[loquat] case class SimpleDataMapping(
  val id: String,
  val inputs: Map[String, AnyRemoteResource],
  val outputs: Map[String, S3Resource]
) extends Serializable {

  def serialize: String = {
    val byteStream = new ByteArrayOutputStream()

    val objOutStream = new ObjectOutputStream(byteStream)
    objOutStream.writeObject(this)
    objOutStream.close()

    new String(
      Base64.getEncoder.encode(byteStream.toByteArray),
      UTF_8
    )
  }
}

case object SimpleDataMapping {

  def deserialize(str: String): SimpleDataMapping = {
    val bytes = Base64.getDecoder.decode(str.getBytes(UTF_8))

    val objInStream = new ObjectInputStream(new ByteArrayInputStream(bytes))
    val sdm = objInStream.readObject.asInstanceOf[SimpleDataMapping]
    objInStream.close()

    sdm
  }
}
