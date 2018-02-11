package ohnosequences.loquat.test

import ohnosequences.datasets._
import ohnosequences.loquat._, utils._, utils.files._, test.data._
import ohnosequences.statika._
import ohnosequences.datasets._
import ohnosequences.cosas._, types._, records._
import concurrent.duration._
import java.util.concurrent._

class LoquatSuite extends org.scalatest.FunSuite {

  test("launching loquat locally and waiting for its termination") {
    config.testLoquat.launchLocally(
      user = config.testUser,
      interval = 30.seconds
    ).map { monitor =>
      monitor.get(15, TimeUnit.MINUTES)
      assert(monitor.isDone)
    }.getOrElse {
      failure("Couldn't launch loquat")
    }
  }
}
