package ohnosequences.loquat.test

object dataMappingsExample {

  import ohnosequences.awstools.s3.ObjectAddress
  import ohnosequences.loquat._, dataMappings._
  import ohnosequences.datasets._, s3Locations._
  import ohnosequences.cosas._, types._, typeSets._, properties._, records._
  import instructionsExample._


  // val dataMapping = DataMapping(id = "dataMapping3498734", instructions = instructs)(
  //   remoteInput =
  //     sample.atS3(ObjectAddress("", "")) :~:
  //     fastq.atS3(ObjectAddress("", "")) :~:
  //     ∅,
  //   remoteOutput =
  //     stats.atS3(ObjectAddress("", "")) :~:
  //     results.atS3(ObjectAddress("", "")) :~:
  //     ∅
  // )
}
