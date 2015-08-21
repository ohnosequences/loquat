
```scala
package ohnosequences.loquat.bundles

case object instructions {

  import ohnosequences.loquat._
  import ohnosequences.datasets._, dataSets._, fileLocations._

  import ohnosequences.cosas._, types._, typeSets._, properties._, records._
  import ops.typeSets._

  import ohnosequences.statika.bundles._
  import ohnosequences.statika.instructions._

  import ohnosequences.awstools.s3.ObjectAddress
  import java.io.File
  import upickle.Js


  trait AnyInstructionsBundle extends AnyBundle {

    type Input <: AnyDataSet
    val  input: Input

    type Output <: AnyDataSet
    val  output: Output

    type OutputFiles = Output#LocationsAt[FileDataLocation]

    // should be provided implicitly:
    val outputFilesToMap: ToMap[OutputFiles, AnyData, FileDataLocation]

    def filesMap(filesSet: OutputFiles): Map[String, File] =
      outputFilesToMap(filesSet).map { case (data, loc) =>
        data.label -> loc.location
      }
```

this is where user describes instructions how to process each dataMapping:
- it can assume that the input files are in place (`inputKey.file`)
- it must produce output files declared in the dataMapping

```scala
    def processDataMapping(dataMappingId: String, workingDir: File): (Results, OutputFiles)
  }

  abstract class InstructionsBundle[
    I <: AnyDataSet,
    O <: AnyDataSet
  ](deps: AnyBundle*)(
    val input: I,
    val output: O
  )(implicit
    val outputFilesToMap: ToMap[O#LocationsAt[FileDataLocation], AnyData, FileDataLocation]
  ) extends Bundle(deps: _*) with AnyInstructionsBundle {

    type Input = I
    type Output = O
  }

}

```




[main/scala/ohnosequences/nisperito/bundles/InstructionsBundle.scala]: InstructionsBundle.scala.md
[main/scala/ohnosequences/nisperito/bundles/LogUploaderBundle.scala]: LogUploaderBundle.scala.md
[main/scala/ohnosequences/nisperito/bundles/ManagerBundle.scala]: ManagerBundle.scala.md
[main/scala/ohnosequences/nisperito/bundles/TerminationDaemonBundle.scala]: TerminationDaemonBundle.scala.md
[main/scala/ohnosequences/nisperito/bundles/WorkerBundle.scala]: WorkerBundle.scala.md
[main/scala/ohnosequences/nisperito/Config.scala]: ../Config.scala.md
[main/scala/ohnosequences/nisperito/dataMappings.scala]: ../dataMappings.scala.md
[main/scala/ohnosequences/nisperito/Nisperito.scala]: ../Nisperito.scala.md
[main/scala/ohnosequences/nisperito/Utils.scala]: ../Utils.scala.md
[test/scala/ohnosequences/nisperito/dataMappings.scala]: ../../../../../test/scala/ohnosequences/nisperito/dataMappings.scala.md
[test/scala/ohnosequences/nisperito/instructions.scala]: ../../../../../test/scala/ohnosequences/nisperito/instructions.scala.md