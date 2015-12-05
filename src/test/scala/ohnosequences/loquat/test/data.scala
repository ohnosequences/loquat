package ohnosequences.loquat.test

import ohnosequences.datasets._, fileType._
import ohnosequences.cosas._, klists._, types._, records._

case object data {

  // inputs:
  case object matrix extends FileData("matrix")(txt)

  // outputs:
  case object transposed extends FileData("transposed")(txt)

}
