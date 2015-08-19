package ohnosequences.nisperito.bundles

case object instructions {

  import ohnosequences.nisperito._, dataSets._

  import ohnosequences.cosas._, types._, typeSets._, properties._, records._
  import ops.typeSets._

  import ohnosequences.statika.bundles._
  import ohnosequences.statika.instructions._

  import ohnosequences.awstools.s3.ObjectAddress
  import java.io.File
  import upickle.Js


  // trait AnyKey extends AnyProperty {
  //   type Raw = File
  // }
  //
  // class InputKey(val label: String) extends AnyKey {
  //   def file(workingDir: File): File = new File(workingDir, s"input/${label}")
  // }
  //
  // trait OutputKey extends AnyKey {
  //   lazy val label: String = this.toString
  // }



  trait AnyInstructionsBundle extends AnyBundle {

    type Input <: AnyDataSet
    val  input: Input

    type Output <: AnyDataSet
    val  output: Output

    case object remoteInput extends S3Locations(input); type remoteInput = remoteInput.type
    case object remoteOutput extends S3Locations(output); type remoteOutput = remoteOutput.type

    // This is for constructing the result of the pipa processor
    case object outputFiles extends FileLocations(output)
    type outputFiles = outputFiles.type

    // trait outputFiles extends AnyRecord {
    //
    //   type Properties = Output
    //   val  properties = output
    //
    //   val vals: Raw
    //
    //   // should be provided implicitly:
    //   val keysToList: Output ToListOf AnyKey
    //   val valsToList: Raw ToListOf AnyDenotation { type Value = File }
    //
    //   /* This will be used later, when uploading results,
    //      to retrieve output files locations by the keys */
    //   lazy final val filesMap: Map[String, File] = {
    //     keysToList(output)
    //       .zip(valsToList(vals))
    //       .map { case (k, f) => (k.label -> f.value) }
    //       .toMap
    //   }
    // }
    //
    // /* Easy to use constructor */
    // case class Files[Vals <: AnyTypeSet](val vals: Vals)
    //  (implicit
    //     val valuesOfProperties: Vals areValuesOf Output,
    //     val keysToList: Output ToListOf AnyKey,
    //     val valsToList: Vals ToListOf AnyDenotation { type Value = File }
    //  ) extends outputFiles {
    //
    //    val label = this.toString
    //    type Raw = Vals
    //  }

    /* this is where user describes instructions how to process each pipa:
       - it can assume that the input files are in place (`inputKey.file`)
       - it must produce output files declared in the pipa */
    def processPipa(pipaId: String, workingDir: File): (Results, ValueOf[outputFiles])
  }

  abstract class InstructionsBundle[
    I <: AnyDataSet,
    O <: AnyDataSet
  ](deps: AnyBundle*)(
    val input: I,
    val output: O
  ) extends Bundle(deps: _*) with AnyInstructionsBundle {

    type Input = I
    type Output = O
  }

}
