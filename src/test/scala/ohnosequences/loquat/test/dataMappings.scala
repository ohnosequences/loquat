package ohnosequences.loquat.test

import ohnosequences.awstools.s3._
import ohnosequences.datasets._, S3DataLocation._
import ohnosequences.cosas._, klists._, types._
import ohnosequences.loquat._, test.data._, test.dataProcessing._

case object dataMappings {

  val input = S3Folder("loquat.testing", "input")
  val output = S3Folder("loquat.testing", "output")

  val dataMapping = DataMapping(processingBundle)(
    remoteInput =
      matrix.inS3(input / matrix.label) ::
      *[AnyDenotation { type Value = S3DataLocation }],
    remoteOutput =
      transposed.inS3(output / transposed.label) ::
      *[AnyDenotation { type Value = S3DataLocation }]
  )

}
