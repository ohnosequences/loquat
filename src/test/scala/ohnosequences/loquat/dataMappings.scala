package ohnosequences.loquat.test

import ohnosequences.awstools.s3._
import ohnosequences.datasets._, S3DataLocation._
import ohnosequences.cosas._, klists._, types._
import ohnosequences.loquat._, test.data._, test.dataProcessing._

case object dataMappings {

  val dataMapping = DataMapping(id = "dataMapping3498734", dataProcessing = instructs)(
    remoteInput =
      S3DataOps(sample).inS3(S3Object("bucket", "key")) ::
      fastq.inS3(S3Object("bucket", "key")) ::
      *[AnyDenotation { type Value = S3DataLocation }],
    remoteOutput =
      stats.inS3(S3Object("bucket", "key")) ::
      results.inS3(S3Object("bucket", "key")) ::
      *[AnyDenotation { type Value = S3DataLocation }]
  )

}
