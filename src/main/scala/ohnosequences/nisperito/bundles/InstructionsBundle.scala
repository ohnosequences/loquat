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


  trait AnyKey extends AnyProperty {
    type Raw = File
    lazy val label: String = this.toString
  }

  trait InputKey extends AnyKey {
    def file: File = new File(s"input/${label}")
  }

  trait OutputKey extends AnyKey



  trait AnyInstructionsBundle extends AnyBundle {

    type InputKeys <: AnyTypeSet.Of[InputKey]
    val  inputKeys: InputKeys

    type OutputKeys <: AnyTypeSet.Of[OutputKey]
    val  outputKeys: OutputKeys

    // This is for constructing the result of the task processor
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
    def processTask(taskId: String): (Results, OutputFiles)
  }

  abstract class InstructionsBundle[
    Is <: AnyTypeSet.Of[InputKey],
    Os <: AnyTypeSet.Of[OutputKey]
  ](deps: AnyBundle*)(
    val inputKeys: Is,
    val outputKeys: Os
  ) extends Bundle(deps: _*) with AnyInstructionsBundle {

    type InputKeys = Is
    type OutputKeys = Os
  }

}
