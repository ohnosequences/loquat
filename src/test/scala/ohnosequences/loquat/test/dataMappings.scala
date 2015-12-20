package ohnosequences.loquat.test

import ohnosequences.awstools.s3._
import ohnosequences.datasets._, S3Resource._
import ohnosequences.cosas._, klists._, types._
import ohnosequences.loquat._, test.data._, test.dataProcessing._

case object dataMappings {

  val input = S3Folder("loquat.testing", "input")
  val output = S3Folder("loquat.testing", "output")

  val dataMapping = DataMapping(processingBundle)(
    remoteInput =
      prefix("viva-loquat") ::
      text("""bluh-blah!!!
      |foo bar
      |qux?
      |¡buh™!
      |""".stripMargin) ::
      matrix(input / matrix.label) ::
      *[AnyDenotation { type Value <: AnyRemoteResource }],
    remoteOutput =
      transposed(output / transposed.label) ::
      *[AnyDenotation { type Value <: S3Resource }]
  )

}
