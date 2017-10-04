package ohnosequences.loquat.test

import ohnosequences.loquat._, utils._, dataMappings._

class Serialization extends org.scalatest.FunSuite {

  val simpleDataMapping = SimpleDataMapping(
    id = dataMapping.label.toString,
    inputs = toMap(dataMapping.remoteInput),
    outputs = toMap(dataMapping.remoteOutput)
  )

  test("Serialize-deserialize") {

    val str = simpleDataMapping.serialize
    val sdm = SimpleDataMapping.deserialize(str)

    assert { simpleDataMapping == sdm }
  }
}
