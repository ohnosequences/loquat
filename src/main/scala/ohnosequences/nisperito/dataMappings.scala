package ohnosequences.loquat

case object dataMappings {

  import bundles._, instructions._

  import ohnosequences.datasets._, dataSets._, s3Locations._
  import ohnosequences.cosas._, types._, typeSets._, properties._, records._
  import ohnosequences.cosas.ops.typeSets._

  import ohnosequences.awstools.s3.ObjectAddress
  import java.io.File
  import upickle.Js


  case class DataMappingResultDescription(
    id: String,
    message: String,
    instanceId: Option[String],
    time: Int
  )

  type RemotesFor[DS <: AnyDataSet] = DS#LocationsAt[S3DataLocation]

  trait AnyDataMapping {

    val id: String

    type Instructions <: AnyInstructionsBundle
    val  instructions: Instructions

    /* These are records with references to the remote locations of
       where to get inputs and where to put outputs of the dataMapping */
    type RemoteInput <: RemotesFor[Instructions#Input]
    val  remoteInput: RemoteInput

    type RemoteOutput <: RemotesFor[Instructions#Output]
    val  remoteOutput: RemoteOutput

    /* These two vals a needed for serialization */
    // should be provided implicitly:
    val inputsToMap:  ToMap[RemoteInput,  AnyData, S3DataLocation]
    val outputsToMap: ToMap[RemoteOutput, AnyData, S3DataLocation]
  }

  case class DataMapping[
    I <: AnyInstructionsBundle,
    RI <: RemotesFor[I#Input],
    RO <: RemotesFor[I#Output]
  ](val id: String,
    val instructions: I,
    val remoteInput: RI,
    val remoteOutput: RO
  )(implicit
    val inputsToMap:  ToMap[RI, AnyData, S3DataLocation],
    val outputsToMap: ToMap[RO, AnyData, S3DataLocation]
  ) extends AnyDataMapping {

    type Instructions = I
    type RemoteInput = RI
    type RemoteOutput = RO
  }


  /* This is easy to parse/serialize */
  protected[loquat]
    case class SimpleDataMapping(
      val id: String,
      val inputs: Map[String, ObjectAddress],
      val outputs: Map[String, ObjectAddress]
    )

  /* and we can transfor any dataMapping to this simple form */
  def simplify(dataMapping: AnyDataMapping): SimpleDataMapping = SimpleDataMapping(
    id = dataMapping.id,
    inputs = dataMapping.inputsToMap(dataMapping.remoteInput)
      .map{ case (data, s3loc) => (data.label -> s3loc.location) },
    outputs = dataMapping.outputsToMap(dataMapping.remoteOutput)
      .map{ case (data, s3loc) => (data.label -> s3loc.location) }
  )

}
