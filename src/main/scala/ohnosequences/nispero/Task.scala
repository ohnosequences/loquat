package ohnosequences.nispero

import ohnosequences.awstools.s3.{ObjectAddress, S3}
import java.io.File
import ohnosequences.nispero.utils.pickles._
import upickle._

sealed trait AnyTask {

  val id: String

  type InputObj
  val inputObjects: Map[String, InputObj]

  val outputObjects: Map[String, ObjectAddress]
}

case class BigTask(
  val id: String,
  val inputObjects: Map[String, ObjectAddress],
  val outputObjects: Map[String, ObjectAddress]
) extends AnyTask { type InputObj = ObjectAddress }

/* The difference here is that we put input objects content in the message itself */
case class TinyTask(
  val id: String,
  val inputObjects: Map[String, String],
  val outputObjects: Map[String, ObjectAddress]
) extends AnyTask { type InputObj = String }


sealed abstract class TaskResult {
  val message: String
}

object TaskResult {

  case class Success(message: String) extends TaskResult
  case class Failure(message: String) extends TaskResult
}

case class TaskResultDescription(
  id: String,
  message: String,
  instanceId: Option[String],
  time: Int
)
