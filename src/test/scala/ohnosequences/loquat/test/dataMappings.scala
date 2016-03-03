package ohnosequences.loquat.test

import ohnosequences.awstools.s3._
import ohnosequences.datasets._, S3Resource._
import ohnosequences.cosas._, klists._, types._
import ohnosequences.loquat._, test.data._, test.dataProcessing._

case object dataMappings {

  val input = S3Folder("loquat.testing", "input")
  val output = S3Folder("loquat.testing", "output")

  val dataMapping = DataMapping("foo", processingBundle)(
    remoteInput = Map(
      // prefix -> MessageResource("viva-loquat"),
      // text -> MessageResource("""bluh-blah!!!
      // |foo bar
      // |qux?
      // |¡buh™!
      // |""".stripMargin),
      // matrix -> S3Resource(input / matrix.label)
    ),
    remoteOutput = Map(
      // transposed -> S3Resource(output / transposed.label)
    )
  )

}
