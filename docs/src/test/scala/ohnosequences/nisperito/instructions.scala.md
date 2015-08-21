
```scala
package ohnosequences.loquat.test

object instructionsExample {

  import ohnosequences.statika.instructions._
  import ohnosequences.loquat._, dataMappings._, bundles._, instructions._
  import ohnosequences.datasets._, dataSets._, fileLocations._
  import ohnosequences.cosas._, types._, typeSets._, properties._, records._
  import java.io.File


  // inputs:
  case object SomeData extends AnyDataType { val label = "someLabel" }
  case object sample extends Data(SomeData, "sample")
  case object fastq extends Data(SomeData, "fastq")

  // outputs:
  case object stats extends Data(SomeData, "stats")
  case object results extends Data(SomeData, "results")

  // instructions:
  case object instructs extends InstructionsBundle()(
    input = sample :^: fastq :^: DNil,
    output = stats :^: results :^: DNil
  ) {

    def install: Results = success("horay!")

    def processDataMapping(dataMappingId: String, workingDir: File): (Results, OutputFiles) = {
      val files =
        stats.inFile(new File(dataMappingId)) :~:
        results.inFile(new File("")) :~:
        ∅
      (success("foo"), files)
    }
  }

  val outputs = instructs.processDataMapping("foo", new File("."))._2

  // NOTE: here we can use Map.apply safely, because we know
  // that filesMap contains all the output by construction
  // val resultsFile: File = outputs.filesMap(results.label)

}

```




[main/scala/ohnosequences/nisperito/bundles/InstructionsBundle.scala]: ../../../../main/scala/ohnosequences/nisperito/bundles/InstructionsBundle.scala.md
[main/scala/ohnosequences/nisperito/bundles/LogUploaderBundle.scala]: ../../../../main/scala/ohnosequences/nisperito/bundles/LogUploaderBundle.scala.md
[main/scala/ohnosequences/nisperito/bundles/ManagerBundle.scala]: ../../../../main/scala/ohnosequences/nisperito/bundles/ManagerBundle.scala.md
[main/scala/ohnosequences/nisperito/bundles/TerminationDaemonBundle.scala]: ../../../../main/scala/ohnosequences/nisperito/bundles/TerminationDaemonBundle.scala.md
[main/scala/ohnosequences/nisperito/bundles/WorkerBundle.scala]: ../../../../main/scala/ohnosequences/nisperito/bundles/WorkerBundle.scala.md
[main/scala/ohnosequences/nisperito/Config.scala]: ../../../../main/scala/ohnosequences/nisperito/Config.scala.md
[main/scala/ohnosequences/nisperito/dataMappings.scala]: ../../../../main/scala/ohnosequences/nisperito/dataMappings.scala.md
[main/scala/ohnosequences/nisperito/Nisperito.scala]: ../../../../main/scala/ohnosequences/nisperito/Nisperito.scala.md
[main/scala/ohnosequences/nisperito/Utils.scala]: ../../../../main/scala/ohnosequences/nisperito/Utils.scala.md
[test/scala/ohnosequences/nisperito/dataMappings.scala]: dataMappings.scala.md
[test/scala/ohnosequences/nisperito/instructions.scala]: instructions.scala.md