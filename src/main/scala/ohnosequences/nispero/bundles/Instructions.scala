package ohnosequences.nispero.bundles

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._


trait AnyInstructions extends AnyBundle {

  val instructions: ohnosequences.nispero.Instructions

  def install: Results = {
    success("instructions installed")
  }
}

abstract class Instructions(deps: AnyBundle*) extends Bundle(deps: _*) with AnyInstructions
