package ohnosequences.loquat.test

import ohnosequences.datasets._
import ohnosequences.cosas._, klists._, types._, records._

case object data {

  case object SomeData extends AnyDataType { val label = "someLabel" }

  // inputs:
  case object sample extends Data(SomeData, "sample")
  case object fastq extends Data(SomeData, "fastq")
  // outputs:
  case object stats extends Data(SomeData, "stats")
  case object results extends Data(SomeData, "results")

}
