
```scala
package ohnosequences.loquat

case object dataProcessing {

  import ohnosequences.datasets._, dataSets._, fileLocations._

  import ohnosequences.cosas._, types._
  import ops.typeSets._

  import ohnosequences.statika.bundles._
  import ohnosequences.statika.instructions._
  import ohnosequences.statika.results._

  import upickle.Js
  import better.files._


  trait AnyProcessingContext {

    val  workingDir: File

    type DataSet <: AnyDataSet
    val  dataSet: DataSet

    type DataFiles  = DataSet#LocationsAt[FileDataLocation]
    val  dataFiles: DataFiles
```

user can get the file corresponding to the given data key

```scala
    def file[K <: AnyData](key: K)(implicit
        lookup: DataFiles Lookup (K := FileDataLocation)
      ): File = lookup(dataFiles).value.location
```

or create a file instance in the orking directory

```scala
    def /(name: String): File = workingDir / name
  }

  case class ProcessingContext[D <: AnyDataSet](
    val dataSet: D,
    val dataFiles: D#LocationsAt[FileDataLocation],
    val workingDir: File
  ) extends AnyProcessingContext { type DataSet = D }



  trait AnyDataProcessingBundle extends AnyBundle {

    type Input <: AnyDataSet
    val  input: Input

    type Output <: AnyDataSet
    val  output: Output

    type InputFiles  = Input#LocationsAt[FileDataLocation]
    type OutputFiles = Output#LocationsAt[FileDataLocation]

    // should be provided implicitly:
    val parseInputFiles: ParseDenotations[InputFiles, File]
    val outputFilesToMap: ToMap[OutputFiles, AnyData, FileDataLocation]

    type Context = ProcessingContext[Input]
```

this is where user describes how to process each dataMapping:
- it takes input data file locations
- it must produce same for the output files

```scala
    def processData(
      dataMappingId: String,
      context: Context
    ): Instructions[OutputFiles]
```

This is a cover-method, which will be used in the worker run-loop

```scala
    final def processFiles(
      dataMappingId: String,
      inputFilesMap: Map[String, File],
      workingDir: File
    ): Result[Map[String, File]] = {
```

This method serialises OutputFiles data mapping to a normal Map

```scala
      def filesMap(filesSet: OutputFiles): Map[String, File] =
        outputFilesToMap(filesSet).map { case (data, loc) =>
          data.label -> loc.location
        }

      parseInputFiles(inputFilesMap) match {
        case Left(err) => Failure(err.toString)
        case Right(inputFiles) => {
          processData(
            dataMappingId,
            ProcessingContext(input, inputFiles, workingDir)
          ).run(workingDir.toJava) match {
            case Failure(tr) => Failure(tr)
            case Success(tr, of) => Success(tr, filesMap(of))
          }
        }
      }

    }
  }

  abstract class DataProcessingBundle[
    I <: AnyDataSet,
    O <: AnyDataSet
  ](deps: AnyBundle*)(
    val input: I,
    val output: O
  )(implicit
    val parseInputFiles: ParseDenotations[I#LocationsAt[FileDataLocation], File],
    val outputFilesToMap: ToMap[O#LocationsAt[FileDataLocation], AnyData, FileDataLocation]
  ) extends Bundle(deps: _*) with AnyDataProcessingBundle {

    type Input = I
    type Output = O
  }

}

```




[main/scala/ohnosequences/loquat/configs.scala]: configs.scala.md
[main/scala/ohnosequences/loquat/daemons.scala]: daemons.scala.md
[main/scala/ohnosequences/loquat/dataMappings.scala]: dataMappings.scala.md
[main/scala/ohnosequences/loquat/dataProcessing.scala]: dataProcessing.scala.md
[main/scala/ohnosequences/loquat/loquats.scala]: loquats.scala.md
[main/scala/ohnosequences/loquat/managers.scala]: managers.scala.md
[main/scala/ohnosequences/loquat/utils.scala]: utils.scala.md
[main/scala/ohnosequences/loquat/workers.scala]: workers.scala.md
[test/scala/ohnosequences/loquat/dataMappings.scala]: ../../../../test/scala/ohnosequences/loquat/dataMappings.scala.md
[test/scala/ohnosequences/loquat/instructions.scala]: ../../../../test/scala/ohnosequences/loquat/instructions.scala.md