package ohnosequences.nisperito.test

object tasksExample {

  import ohnosequences.awstools.s3.ObjectAddress
  import ohnosequences.nisperito._, tasks._
  import ohnosequences.cosas._, types._, typeSets._, properties._, records._
  import instructionsExample._


  val task = Task(
    id = "task3498734",
    instructions = instructs,
    inputRefs =
      S3Ref(sample, ObjectAddress("", "")) :~:
      S3Ref(fastaq, ObjectAddress("", "")) :~:
      ∅,
    outputRefs =
      S3Ref(stats, ObjectAddress("", "")) :~:
      S3Ref(results, ObjectAddress("", "")) :~:
      ∅
  )
}
