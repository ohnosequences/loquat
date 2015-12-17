package ohnosequences.loquat.test

import ohnosequences.datasets._
import ohnosequences.cosas._, klists._, types._, records._

case object data {

  // inputs:
  case object prefix extends Data("prefix")
  case object text extends Data("text")
  case object matrix extends FileData("matrix")("txt")

  // outputs:
  case object transposed extends FileData("transposed")("txt")

}
