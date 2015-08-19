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


  trait AnyPipa {

    val id: String

    type Instructions <: AnyInstructionsBundle
    val  instructions: Instructions

    /* These are records with references to the remote locations of
       where to get inputs and where to put outputs of the pipa */
    // NOTE: at the moment we restrict refs to be only S3 objects
    val remoteInputVal: ValueOf[Instructions#remoteInput]
    val remoteOutputVal: ValueOf[Instructions#remoteOutput]



    /* These two vals a needed for serialization */
    // should be provided implicitly:
    // val inputsToList: InputRefs ToListOf AnyS3Ref
    // val outputsToList: OutputRefs ToListOf AnyS3Ref
  }

  // case class Pipa[
  //   I <: AnyInstructionsBundle,
  //   IR <: AnyTypeSet.Of[AnyS3Ref],
  //   OR <: AnyTypeSet.Of[AnyS3Ref]
  // ](val id: String,
  //   val instructions: I,
  //   val inputRefs: IR,
  //   val outputRefs: OR
  // )(implicit
  //   val checkInputKeys: TypesOf[IR] { type Out = I#InputKeys },
  //   val checkOutputKeys: TypesOf[OR] { type Out = I#OutputKeys },
  //   val inputsToList: IR ToListOf AnyS3Ref,
  //   val outputsToList: OR ToListOf AnyS3Ref
  // ) extends AnyPipa {
  //
  //   type Instructions = I
  //   type InputRefs = IR
  //   type OutputRefs = OR
  // }


  /* This is easy to parse/serialize */
  protected[nisperito]
    case class SimplePipa(
      val id: String,
      val inputs: Map[String, ObjectAddress],
      val outputs: Map[String, ObjectAddress]
    )

  /* and we can transfor any pipa to this simple form */
  // implicit def simplify(pipa: AnyPipa): SimplePipa =
  //   SimplePipa(
  //     id = pipa.id,
  //     inputs = pipa.inputsToList(pipa.inputRefs)
  //       .map{ case ref => (ref.key.label -> ref.value) }.toMap,
  //     outputs = pipa.outputsToList(pipa.outputRefs)
  //       .map{ case ref => (ref.key.label -> ref.value) }.toMap
  //   )

}
