package ohnosequences.nisperito.test

object tasksExample {

  import ohnosequences.awstools.s3.ObjectAddress
  import ohnosequences.nisperito._, tasks._
  import ohnosequences.cosas._, types._, typeSets._, properties._, records._
  import instructionsExample._


  val task = CoolTask(
    id = "task3498734",
    instructions = instructs,
    inputRemotes =
      sample.remote(ObjectAddress("", "")) :~:
      fastaq.remote(ObjectAddress("", "")) :~:
      ∅,
    outputRemotes =
      stats.remote(ObjectAddress("", "")) :~:
      results.remote(ObjectAddress("", "")) :~:
      ∅
  )
}
