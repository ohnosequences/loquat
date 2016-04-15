package ohnosequences.loquat.test

import better.files._
import ohnosequences.awstools._, s3._, sqs._
import ohnosequences.datasets._, S3Resource._
import ohnosequences.cosas._, klists._, types._
import ohnosequences.loquat._, test.data._, test.dataProcessing._
// import com.amazonaws.services.sqs.model.SendMessageResult


class PostingDataMappings extends org.scalatest.FunSuite {

  lazy val creds      = new com.amazonaws.auth.profile.ProfileCredentialsProvider("default")
  lazy val clientConf = new com.amazonaws.ClientConfiguration

  lazy val connections  : Int = 400
  lazy val customConf = clientConf.withMaxConnections(connections)

  val sqs: SQS = new SQS(
    new com.amazonaws.services.sqs.AmazonSQSClient(clientConf)
      .withRegion(com.amazonaws.regions.Regions.EU_WEST_1)
  )

  lazy val queueName: String = "loquat-testing-parallel-2"
  lazy val queue    : Queue  = (sqs getQueueByName queueName) getOrElse (sqs createQueue queueName)

  val input   = S3Folder("loquat.testing", "input")
  val output  = S3Folder("loquat.testing", "output")


  def dataMappings(mssgNumber: Int) = List.fill(mssgNumber)(
    DataMapping("foo", processingBundle)(
      remoteInput = Map(
        prefix -> MessageResource("viva-loquat"),
        text -> MessageResource("""bluh-blah!!!
        |foo bar
        |qux?
        |¡buh™!
        |""".stripMargin),
        matrix -> S3Resource(input/matrix.label)
      ),
      remoteOutput = Map(
        transposed -> S3Resource(output/transposed.label)
      )
    )
  )

  def postToQueue(dm: DataMapping[processingBundle.type], ix: Int): Int = {

      queue.sendMessage(
        upickle.default.write[SimpleDataMapping](
          SimpleDataMapping(
            id = ix.toString,
            inputs  = ohnosequences.loquat.utils.toMap(dm.remoteInput),
            outputs = ohnosequences.loquat.utils.toMap(dm.remoteOutput)
          )
        )
      )

      ix
  }

  import java.util.concurrent.Executors
  import concurrent._, duration._

  def runTest(threadNumber: Int, mssgNumber: Int, timeout: Duration): Unit = {

    implicit val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(threadNumber))

    def postToQueueF(dm: DataMapping[processingBundle.type], id: Int): Future[Int] =
      Future { postToQueue(dm,id) }

    def sendMssgsF(mssgNumber: Int): List[Future[Int]] =
      dataMappings(mssgNumber).zipWithIndex.map {
        case (dm,i) => postToQueueF(dm,i)
      }

    val mssgIdsF = Future.sequence(sendMssgsF(mssgNumber))

    val start = System.nanoTime
    val block = Await.result(mssgIdsF, timeout)
    val took  = System.nanoTime - start

    println { s"${threadNumber} threads, ${block.toSet.size} messages sent, took approx ${(took nanos).toMillis} milis" }

    ec.shutdown()
  }

  test("post dataMappings in parallel") {

    // NOTE too slow
    // runTest( 1,   400, 120 seconds )
    // runTest( 4,   400, 60 seconds )
    // runTest( 8,   400, 14 seconds )
    // runTest( 16,  400, 8 seconds )
    // runTest( 24,  400, 8 seconds )
    // runTest( 32,  800, 8 seconds )
    // runTest( 48,  400, 8 seconds )
    // runTest( 64,  400, 8 seconds )
    // runTest( 72,  400, 8 seconds )
    // runTest( 86,  400, 8 seconds )
    runTest( 94,  400, 8 seconds )

  }
}
