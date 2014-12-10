package ohnosequences.nispero

import ohnosequences.awstools.s3.{S3, ObjectAddress}
import java.io.{PrintWriter, File}
import scala.collection.mutable.ListBuffer
// import ohnosequences.nispero.utils.JSON

abstract class TasksProvider {  p =>
  def tasks(s3: S3): Stream[Task]

  def ~(q: TasksProvider) = new TasksProvider {
    def tasks(s3: S3): Stream[Task] = p.tasks(s3) ++ q.tasks(s3)
  }
}

// case class TasksProvider(tasks: S3 => Stream[Task])

object TasksProvider {
  def flatten(qs: List[TasksProvider]): TasksProvider = new TasksProvider {
    def tasks(s3: S3): Stream[Task] =  qs.foldLeft(Stream[Task]()){ (acc, p) => acc ++ p.tasks(s3) }
  }

  // val empty: TasksProvider = TasksProvider{ _ => Stream[Task]() }
}

case object EmptyTasks extends TasksProvider {
  def tasks(s3: S3): Stream[Task] = Stream()
}
