package ohnosequences.nisperito

case object tasks {

  import bundles._, instructions._
  import ohnosequences.awstools.s3.ObjectAddress
  import ohnosequences.cosas._, types._, typeSets._, properties._, records._
  import ohnosequences.cosas.ops.typeSets._
  import java.io.File

  sealed trait AnyTask {

    val id: String

    type InputObj
    val inputObjects: Map[String, InputObj]

    val outputObjects: Map[String, ObjectAddress]
  }

  case class TinyTask(
    val id: String,
    val inputObjects: Map[String, String],
    val outputObjects: Map[String, ObjectAddress]
  ) extends AnyTask { type InputObj = String }

  case class BigTask(
    val id: String,
    val inputObjects: Map[String, ObjectAddress],
    val outputObjects: Map[String, ObjectAddress]
  ) extends AnyTask { type InputObj = ObjectAddress }



  case class TaskResultDescription(
    id: String,
    message: String,
    instanceId: Option[String],
    time: Int
  )


  trait AnyCoolTask {

    val id: String

    type Instructions <: AnyInstructionsBundle
    val  instructions: Instructions

    /* These are records with references to the remote locations of
       where to get inputs and where to put outputs of the task */
    type InputRemotes <: AnyTypeSet.Of[AnyRemote]
    val  inputRemotes: InputRemotes

    type OutputRemotes <: AnyTypeSet.Of[AnyRemote]
    val  outputRemotes: OutputRemotes

    /* These two implicits check that the remote references records' keys
       corespond to the keys from the instructinos bundle */
    // should be provided implicitly:
    implicit val checkInputKeys: TypesOf[InputRemotes] { type Out = Instructions#InputKeys }
    implicit val checkOutputKeys: TypesOf[OutputRemotes] { type Out = Instructions#OutputKeys }
  }

  case class CoolTask[
    I <: AnyInstructionsBundle,
    IR <: AnyTypeSet.Of[AnyRemote],
    OR <: AnyTypeSet.Of[AnyRemote]
  ](val id: String,
    val instructions: I,
    val inputRemotes: IR,
    val outputRemotes: OR
  )(implicit
    val checkInputKeys: TypesOf[IR] { type Out = I#InputKeys },
    val checkOutputKeys: TypesOf[OR] { type Out = I#OutputKeys }
  ) extends AnyCoolTask {

    type Instructions = I
    type InputRemotes = IR
    type OutputRemotes = OR
  }


}
