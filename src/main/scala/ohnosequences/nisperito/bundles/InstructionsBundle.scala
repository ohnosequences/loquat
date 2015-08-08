package ohnosequences.nisperito.bundles

case object instructions {

  import ohnosequences.nisperito._

  import ohnosequences.cosas._, types._, typeSets._, properties._, records._
  import ops.typeSets._

  import ohnosequences.statika.bundles._
  import ohnosequences.statika.instructions._

  import ohnosequences.awstools.s3.ObjectAddress
  import java.io.File


  sealed trait AnyRemote extends AnyDenotation {
    type Tpe <: AnyKey
    type Value = Tpe#RemoteVal
  }
  case class Remote[K <: AnyKey](val value: K#RemoteVal) extends AnyRemote { type Tpe = K }

  trait AnyKey extends AnyProperty {
    type Raw = File
    lazy val label: String = this.toString

    type RemoteVal
    def remote(r: RemoteVal): Remote[this.type] = Remote[this.type](r)
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
    trait OutputFiles extends AnyRecord {

      type Properties = OutputKeys
      val  properties = outputKeys
    }

    // Easy to use constructor
    // TODO: serialization of the record to a Map on construction
    case class Files[Vals <: AnyTypeSet](vals: Vals)
      (implicit val valuesOfProperties: Vals areValuesOf OutputKeys)
        extends OutputFiles {

          val label = this.toString
          type Raw = Vals
        }

    /* this is where user describes instructions how to process each task:
       - it can assume that the input files are in place (`inputKey.file`)
       - it must produce output files declared in the task
    */
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
