package ohnosequences.nisperito.bundles

case object instructions {

  import ohnosequences.nisperito._

  import ohnosequences.cosas._, types._, typeSets._, properties._, records._
  import ops.typeSets._

  import ohnosequences.statika.bundles._
  import ohnosequences.statika.instructions._

  import ohnosequences.awstools.s3.ObjectAddress
  import java.io.File
  import upickle.Js


  sealed trait AnyRemote extends AnyDenotation {
    type Tpe <: AnyKey
    type Value = Tpe#RemoteVal

    // For serialization:
    val label: String
    val valueWriter: upickle.default.Writer[Value]
  }
  case class Remote[K <: AnyKey](
    val label: String,
    val value: K#RemoteVal
  )(implicit
    val valueWriter: upickle.default.Writer[K#RemoteVal]
  ) extends AnyRemote { type Tpe = K }

  object AnyRemote {
    implicit val writer = upickle.default.Writer[AnyRemote]{ r =>
      Js.Obj(r.label -> r.valueWriter.write(r.value))
    }
  }

  trait AnyKey extends AnyProperty {
    type Raw = File
    lazy val label: String = this.toString

    type RemoteVal
    def remote(r: RemoteVal)(implicit
      valueWriter: upickle.default.Writer[RemoteVal]
    ): Remote[this.type] =
       Remote[this.type](label, r)(valueWriter)
  }

  object AnyKey {
    implicit val keyWriter = upickle.default.Writer[AnyKey]{ k => Js.Str(k.label) }
  }

  trait AnyInputKey extends AnyKey {
    def file: File = new File(s"input/${label}")
  }
  trait S3InputKey extends AnyInputKey { type RemoteVal = ObjectAddress }


  trait AnyOutputKey extends AnyKey
  trait S3OutputKey extends AnyOutputKey { type RemoteVal = ObjectAddress }



  trait AnyInstructionsBundle extends AnyBundle {

    type InputKeys <: AnyTypeSet.Of[AnyInputKey]
    val  inputKeys: InputKeys

    type OutputKeys <: AnyTypeSet.Of[AnyOutputKey]
    val  outputKeys: OutputKeys

    // This is for constructing the result of the task processor
    // abstract class FilesFor[Keys <: AnyTypeSet.Of[AnyKey]](keys: Keys) extends AnyRecord {
    trait OutputFiles extends AnyRecord {

      type Properties = OutputKeys
      val  properties = outputKeys

      val vals: Raw

      // should be provided implicitly:
      val keysToList: OutputKeys ToListOf AnyKey
      val valsToList: Raw ToListOf AnyDenotation { type Value = File }

      /* This will be used later, when uploading results,
         to retrieve output files locations by the keys */
      lazy final val filesMap: Map[String, File] = {
        keysToList(outputKeys)
          .zip(valsToList(vals))
          .map { case (k, f) => (k.label -> f.value) }
          .toMap
      }
    }

    /* Easy to use constructor */
    case class Files[Vals <: AnyTypeSet](val vals: Vals)
     (implicit
        val valuesOfProperties: Vals areValuesOf OutputKeys,
        val keysToList: OutputKeys ToListOf AnyKey,
        val valsToList: Vals ToListOf AnyDenotation { type Value = File }
     ) extends OutputFiles {

       val label = this.toString
       type Raw = Vals
     }

    /* this is where user describes instructions how to process each task:
       - it can assume that the input files are in place (`inputKey.file`)
       - it must produce output files declared in the task */
    def processTask: (Results, OutputFiles)
  }

  abstract class InstructionsBundle[
    Is <: AnyTypeSet.Of[AnyInputKey],
    Os <: AnyTypeSet.Of[AnyOutputKey]
  ](deps: AnyBundle*)(
    val inputKeys: Is,
    val outputKeys: Os
  ) extends Bundle(deps: _*) with AnyInstructionsBundle {

    type InputKeys = Is
    type OutputKeys = Os
  }

}
