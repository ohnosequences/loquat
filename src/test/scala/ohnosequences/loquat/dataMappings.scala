package ohnosequences.loquat.test

object dataMappingsExample {

  import ohnosequences.awstools.s3._
  import ohnosequences.loquat._, dataMappings._
  import ohnosequences.datasets._, s3Locations._
  import ohnosequences.cosas._, typeSets._
  import instructionsExample._


  val dataMapping = DataMapping(id = "dataMapping3498734", dataProcessing = instructs)(
    remoteInput =
      sample.inS3(S3Object("bucket", "key")) :~:
      fastq.inS3(S3Object("bucket", "key")) :~:
      ∅,
    remoteOutput =
      stats.inS3(S3Object("bucket", "key")) :~:
      results.inS3(S3Object("bucket", "key")) :~:
      ∅
  )

}
