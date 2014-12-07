package ohnosequences.nispero

import ohnosequences.awstools.s3.{S3, ObjectAddress}
import java.io.{PrintWriter, File}
import net.liftweb.json.JsonParser.ParseException
import scala.collection.mutable.ListBuffer
import ohnosequences.nispero.utils.JSON

abstract class TasksProvider {  p =>
  def tasks(s3: S3): Stream[Task]

  def ~(q: TasksProvider) = new TasksProvider {
    def tasks(s3: S3): Stream[Task] = p.tasks(s3) ++ q.tasks(s3)
  }
}

object TasksProvider {
  def flatten(qs: List[TasksProvider]): TasksProvider = new TasksProvider {
    def tasks(s3: S3): Stream[Task] =  qs.flatMap(_.tasks(s3))
  }
}

case object EmptyTasks extends TasksProvider {
  def tasks(s3: S3): Stream[Task] = Stream()
}
