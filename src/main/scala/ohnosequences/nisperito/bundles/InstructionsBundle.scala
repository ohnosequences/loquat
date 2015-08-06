package ohnosequences.nisperito.bundles

import ohnosequences.nisperito._, tasks._

import ohnosequences.cosas._, typeSets._, properties._, records._

import ohnosequences.statika.bundles._
import ohnosequences.statika.instructions._

import java.io.File


trait AnyInstructionsBundle extends AnyBundle {

  type Inputs = AnyTypeSet.Of[InputKey]
  type Outputs = AnyTypeSet.Of[OutputKey]

  /* this is where user describes instructions how to process each task:
     - it gets the list of input files
     - it must produce output files declared in the task
  */
  def processTask: (Results, FilesFor[Outputs])
}

abstract class InstructionsBundle(deps: AnyBundle*)
  extends Bundle(deps: _*) with AnyInstructionsBundle
