
```scala
package ohnosequences.loquat.test

object instructionsExample {

  import ohnosequences.loquat._, dataProcessing._
  import ohnosequences.statika.instructions._
  import ohnosequences.datasets._, dataSets._, fileLocations._
  import ohnosequences.cosas._, typeSets._
  import better.files._


  // inputs:
  case object SomeData extends AnyDataType { val label = "someLabel" }
  case object sample extends Data(SomeData, "sample")
  case object fastq extends Data(SomeData, "fastq")

  // outputs:
  case object stats extends Data(SomeData, "stats")
  case object results extends Data(SomeData, "results")

  // instructions:
  case object instructs extends DataProcessingBundle()(
    input = sample :^: fastq :^: DNil,
    output = stats :^: results :^: DNil
  ) {

    def instructions: AnyInstructions = say("horay!")

    def processData(dataMappingId: String, context: Context): Instructions[OutputFiles] = {
      success("foo",
        stats.inFile(File(dataMappingId)) :~:
        results.inFile(File(".")) :~:
        ∅
      )
    }
  }

  // val outputs = instructs.processDataMapping("foo", new File("."))._2

  // NOTE: here we can use Map.apply safely, because we know
  // that filesMap contains all the output by construction
  // val resultsFile: File = outputs.filesMap(results.label)

}

```




[test/scala/ohnosequences/loquat/dataMappings.scala]: dataMappings.scala.md
[test/scala/ohnosequences/loquat/instructions.scala]: instructions.scala.md
[main/scala/ohnosequences/loquat/dataProcessing.scala]: ../../../../main/scala/ohnosequences/loquat/dataProcessing.scala.md
[main/scala/ohnosequences/loquat/workers.scala]: ../../../../main/scala/ohnosequences/loquat/workers.scala.md
[main/scala/ohnosequences/loquat/managers.scala]: ../../../../main/scala/ohnosequences/loquat/managers.scala.md
[main/scala/ohnosequences/loquat/daemons.scala]: ../../../../main/scala/ohnosequences/loquat/daemons.scala.md
[main/scala/ohnosequences/loquat/loquats.scala]: ../../../../main/scala/ohnosequences/loquat/loquats.scala.md
[main/scala/ohnosequences/loquat/utils.scala]: ../../../../main/scala/ohnosequences/loquat/utils.scala.md
[main/scala/ohnosequences/loquat/dataMappings.scala]: ../../../../main/scala/ohnosequences/loquat/dataMappings.scala.md
[main/scala/ohnosequences/loquat/configs.scala]: ../../../../main/scala/ohnosequences/loquat/configs.scala.md