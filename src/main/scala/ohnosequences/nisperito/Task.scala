package ohnosequences.nisperito

case object tasks {

  import bundles._, instructions._

  import ohnosequences.cosas._, types._, typeSets._, properties._, records._
  import ohnosequences.cosas.ops.typeSets._

  import ohnosequences.awstools.s3.ObjectAddress
  import java.io.File
  import upickle.Js


  case class TaskResultDescription(
    id: String,
    message: String,
    instanceId: Option[String],
    time: Int
  )

  // TODO: ops for AnyKey to construct ref values
  sealed trait AnyKeyRef extends AnyDenotation {
    type Key <: AnyKey
    val  key: Key

    type Tpe = Key
  }

  abstract class KeyRef[V] extends AnyKeyRef { type Value = V }

  trait AnyS3Ref extends KeyRef[ObjectAddress]

  case class S3Ref[K <: AnyKey](val key: K, val value: ObjectAddress)
    extends AnyS3Ref { type Key = K }



  trait AnyTask {

    val id: String

    type Instructions <: AnyInstructionsBundle
    val  instructions: Instructions

    /* These are records with references to the remote locations of
       where to get inputs and where to put outputs of the task */
    // NOTE: at the moment we restrict refs to be only S3 objects
    type InputRefs <: AnyTypeSet.Of[AnyS3Ref]
    val  inputRefs: InputRefs

    type OutputRefs <: AnyTypeSet.Of[AnyS3Ref]
    val  outputRefs: OutputRefs

    /* These two implicits check that the remote references records' keys
       corespond to the keys from the instructinos bundle */
    // should be provided implicitly:
    val checkInputKeys: TypesOf[InputRefs] { type Out = Instructions#InputKeys }
    val checkOutputKeys: TypesOf[OutputRefs] { type Out = Instructions#OutputKeys }

    /* These two vals a needed for serialization */
    // should be provided implicitly:
    val inputsToList: InputRefs ToListOf AnyS3Ref
    val outputsToList: OutputRefs ToListOf AnyS3Ref
  }

  case class Task[
    I <: AnyInstructionsBundle,
    IR <: AnyTypeSet.Of[AnyS3Ref],
    OR <: AnyTypeSet.Of[AnyS3Ref]
  ](val id: String,
    val instructions: I,
    val inputRefs: IR,
    val outputRefs: OR
  )(implicit
    val checkInputKeys: TypesOf[IR] { type Out = I#InputKeys },
    val checkOutputKeys: TypesOf[OR] { type Out = I#OutputKeys },
    val inputsToList: IR ToListOf AnyS3Ref,
    val outputsToList: OR ToListOf AnyS3Ref
  ) extends AnyTask {

    type Instructions = I
    type InputRefs = IR
    type OutputRefs = OR
  }


  /* This is easy to parse/serialize */
  protected[nisperito]
    case class SimpleTask(
      val id: String,
      val inputs: Map[String, ObjectAddress],
      val outputs: Map[String, ObjectAddress]
    )

  /* and we can transfor any task to this simple form */
  implicit def simplify(task: AnyTask): SimpleTask =
    SimpleTask(
      id = task.id,
      inputs = task.inputsToList(task.inputRefs)
        .map{ case ref => (ref.key.label -> ref.value) }.toMap,
      outputs = task.outputsToList(task.outputRefs)
        .map{ case ref => (ref.key.label -> ref.value) }.toMap
    )

}
