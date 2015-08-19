package ohnosequences.nisperito

case object pipas {

  import bundles._, instructions._, dataSets._

  import ohnosequences.cosas._, types._, typeSets._, properties._, records._
  import ohnosequences.cosas.ops.typeSets._

  import ohnosequences.awstools.s3.ObjectAddress
  import java.io.File
  import upickle.Js


  case class PipaResultDescription(
    id: String,
    message: String,
    instanceId: Option[String],
    time: Int
  )

  type RemotesFor[DS <: AnyDataSet] = DS#LocationsAt[S3DataLocation]

  trait AnyPipa {

    val id: String

    type Instructions <: AnyInstructionsBundle
    val  instructions: Instructions

    /* These are records with references to the remote locations of
       where to get inputs and where to put outputs of the pipa */
    // NOTE: at the moment we restrict refs to be only S3 objects
    val remoteInput: RemotesFor[Instructions#Input]
    val remoteOutput: RemotesFor[Instructions#Output]

    /* These two vals a needed for serialization */
    // should be provided implicitly:
    val inputsToMap:  ToMap[RemotesFor[Instructions#Input],  AnyData, S3DataLocation]
    val outputsToMap: ToMap[RemotesFor[Instructions#Output], AnyData, S3DataLocation]
  }

  case class Pipa[
    I <: AnyInstructionsBundle,
    RI <: RemotesFor[I#Input],
    RO <: RemotesFor[I#Output]
  ](val id: String,
    val instructions: I,
    val remoteInput: RemotesFor[I#Input],
    val remoteOutput: RemotesFor[I#Output]
  )(implicit
    val inputsToMap:  ToMap[RemotesFor[I#Input], AnyData, S3DataLocation],
    val outputsToMap: ToMap[RemotesFor[I#Output], AnyData, S3DataLocation]
  ) extends AnyPipa {

    type Instructions = I
  }


  /* This is easy to parse/serialize */
  protected[nisperito]
    case class SimplePipa(
      val id: String,
      val inputs: Map[String, ObjectAddress],
      val outputs: Map[String, ObjectAddress]
    )

  /* and we can transfor any pipa to this simple form */
  def simplify(pipa: AnyPipa): SimplePipa = SimplePipa(
    id = pipa.id,
    inputs = pipa.inputsToMap(pipa.remoteInput)
      .map{ case (data, s3loc) => (data.label -> s3loc.location) },
    outputs = pipa.outputsToMap(pipa.remoteOutput)
      .map{ case (data, s3loc) => (data.label -> s3loc.location) }
  )

}
