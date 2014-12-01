package ohnosequences.nispero

import ohnosequences.awstools.s3.{ObjectAddress, S3}
import java.io.File

/* The difference here is that we put input objects content in the message itself */
case class Task(
  val id: String,
  val inputObjects: Map[String, String],
  val outputObjects: Map[String, ObjectAddress]
)

trait Instructions {
  def execute(s3: S3, task: Task, workingDir: File = new File(".")): TaskResult
}

sealed abstract class TaskResult {
  val message: String
}
case class Success(message: String) extends TaskResult
case class Failure(message: String) extends TaskResult

case class TaskResultDescription(
  id: String,
  message: String,
  instanceId: Option[String],
  time: Int
)
