package ohnosequences.loquat.test

import ohnosequences.datasets._, files._, fileType._
import ohnosequences.cosas._, klists._, types._, records._

case object data {

  case object SomeData extends AnyDataType { val label = "someLabel" }

  case class Sample(id: String) extends Type[Any](id)

  // inputs:
  case class reads1(sample: Sample) extends FileData("reads1")(gz)
  case class reads2(sample: Sample) extends FileData("reads2")(gz)

  // outputs:
  case object stats extends FileData("stats")(txt)
  case class mergedReads(sample: Sample) extends FileData("merged")(fa)

}
