
```scala
package ohnosequences.loquat

import utils._

import ohnosequences.datasets._

import ohnosequences.cosas._, types._, typeUnions._, records._, fns._, klists._
// import ops.typeSets._

import ohnosequences.statika._

import upickle.Js
import better.files._


trait AnyProcessingContext {

  val workingDir: File
  val inputFiles: Map[String, File]

  type DataSet <: AnyDataSet
```

user can get the file corresponding to the given data key

```scala
  def inputFile[K <: AnyData](key: K)(implicit
    isIn: K isOneOf DataSet#Keys#Types#AllTypes
  ): File = inputFiles(key.label)
```

or create a file instance in the orking directory

```scala
  def /(name: String): File = workingDir / name
}

case class ProcessingContext[D <: AnyDataSet](
  val workingDir: File,
  val inputFiles: Map[String, File]
) extends AnyProcessingContext { type DataSet = D }


trait AnyDataProcessingBundle extends AnyBundle {

  type Input <: AnyDataSet
  val  input: Input

  type Output <: AnyDataSet
  val  output: Output

  type OutputFiles = ResourcesSet[Output, FileResource]

  // this is where you define what to do
  def process(context: ProcessingContext[Input]): AnyInstructions { type Out <: OutputFiles }


  final def runProcess(workingDir: File, inputFiles: Map[String, File]): Result[Map[String, File]] = {
    process(ProcessingContext[Input](workingDir, inputFiles))
      .run(workingDir.toJava) match {
        case Failure(tr) => Failure(tr)
        case Success(tr, of) => Success(tr,
          of.asList.map { d => (d.tpe.label, d.value.resource) }.toMap
        )
      }
  }

}

abstract class DataProcessingBundle[
  I <: AnyDataSet,
  O <: AnyDataSet
](deps: AnyBundle*)(
  val input: I,
  val output: O
) extends Bundle(deps: _*) with AnyDataProcessingBundle {

  type Input = I
  type Output = O
}

```




[main/scala/ohnosequences/loquat/configs/autoscaling.scala]: configs/autoscaling.scala.md
[main/scala/ohnosequences/loquat/configs/general.scala]: configs/general.scala.md
[main/scala/ohnosequences/loquat/configs/loquat.scala]: configs/loquat.scala.md
[main/scala/ohnosequences/loquat/configs/resources.scala]: configs/resources.scala.md
[main/scala/ohnosequences/loquat/configs/termination.scala]: configs/termination.scala.md
[main/scala/ohnosequences/loquat/configs/user.scala]: configs/user.scala.md
[main/scala/ohnosequences/loquat/dataMappings.scala]: dataMappings.scala.md
[main/scala/ohnosequences/loquat/dataProcessing.scala]: dataProcessing.scala.md
[main/scala/ohnosequences/loquat/logger.scala]: logger.scala.md
[main/scala/ohnosequences/loquat/loquats.scala]: loquats.scala.md
[main/scala/ohnosequences/loquat/manager.scala]: manager.scala.md
[main/scala/ohnosequences/loquat/terminator.scala]: terminator.scala.md
[main/scala/ohnosequences/loquat/utils.scala]: utils.scala.md
[main/scala/ohnosequences/loquat/worker.scala]: worker.scala.md
[test/scala/ohnosequences/loquat/test/config.scala]: ../../../../test/scala/ohnosequences/loquat/test/config.scala.md
[test/scala/ohnosequences/loquat/test/data.scala]: ../../../../test/scala/ohnosequences/loquat/test/data.scala.md
[test/scala/ohnosequences/loquat/test/dataMappings.scala]: ../../../../test/scala/ohnosequences/loquat/test/dataMappings.scala.md
[test/scala/ohnosequences/loquat/test/dataProcessing.scala]: ../../../../test/scala/ohnosequences/loquat/test/dataProcessing.scala.md
[test/scala/ohnosequences/loquat/test/md5.scala]: ../../../../test/scala/ohnosequences/loquat/test/md5.scala.md