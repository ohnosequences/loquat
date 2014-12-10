package ohnosequences.nispero.manager

import ohnosequences.nispero.Task

case class RawCommand(command: String, arg: String)

object RawCommand {
  def UnDeploy(reason: String) = RawCommand("UnDeploy", reason)
  def AddTasks(tasks: List[Task]) = RawCommand("AddTasks", tasks.toString())
  def ChangeCapacity(capacity: Int) = RawCommand("ChangeCapacity", capacity.toString)
}
