
```scala
package ohnosequences.loquat

import scala.concurrent.duration._




trait AnyTerminationReason {
  def check: Boolean
  def msg: String
}

case class TerminateAfterInitialDataMappings(
  val isOn: Boolean,
  val initialCount: Int,
  val  inputQNumbers: QueueNumbers,
  val outputQNumbers: QueueNumbers
) extends AnyTerminationReason {

  def check: Boolean = { isOn &&
    inputQNumbers.inFlight  == 0 &&
    inputQNumbers.available == 0 &&
    outputQNumbers.inFlight == 0 &&
    outputQNumbers.available >= initialCount
  }

  def msg: String = s"""|Termination after successfully processing all the initial data mappings.
    |  Initial data mappings count: ${initialCount}
    |  Successful results: ${outputQNumbers.available}
    |""".stripMargin
}

case class TerminateWithTooManyErrors(
  val errorsThreshold: Option[Int],
  val failedCount: Int
) extends AnyTerminationReason {

  def check: Boolean = errorsThreshold.map{ failedCount >= _ }.getOrElse(false)

  def msg: String = s"""|Termination due to too many errors.
    |  Errors threshold: ${errorsThreshold}
    |  Failed results count: ${failedCount}
    |""".stripMargin
}

case class TerminateAfterGlobalTimeout(
  val globalTimeout: Option[FiniteDuration],
  val startTime: Option[FiniteDuration]
) extends AnyTerminationReason {

  def check: Boolean = (startTime, globalTimeout) match {
    case (Some(timestamp), Some(globalTimeout)) =>
      (System.currentTimeMillis - timestamp.toMillis) > globalTimeout.toMillis
    case _ => false
  }

  def msg: String = s"Termination due to the global timeout: ${globalTimeout.getOrElse(0.seconds)}"
}

case object TerminateManually extends AnyTerminationReason {
  def check: Boolean = true
  def msg: String = "Manual termination"
}
```

Configuration of termination conditions

```scala
case class TerminationConfig(
  // if true loquat will terminate after solving all initial tasks
  terminateAfterInitialDataMappings: Boolean,
  // if true loquat will terminate after errorQueue will contain more unique messages then threshold
  errorsThreshold: Option[Int] = None,
  // maximum time for processing one task
  taskProcessingTimeout: Option[FiniteDuration] = None,
  // maximum time for everything
  globalTimeout: Option[FiniteDuration] = None
) extends Config("Termination config")() {

  def validationErrors(aws: AWSClients): Seq[String] = {
    val treshholdErr = errorsThreshold match {
      case Some(n) if n <= 0 => Seq(s"Errors threshold has to be positive: ${n}")
      case _ => Seq()
    }

    val localTimeoutErr = taskProcessingTimeout match {
      case Some(time) if (
          time <= 0.seconds ||
          time > 12.hours
        ) => Seq(s"Task processing timeout [${time}] has to be between 0 seconds and 12 hours")
      case _ => Seq()
    }

    val globalTimeoutErr = globalTimeout match {
      case Some(time) if (
          time <= 0.seconds ||
          time > 12.hours
        ) => Seq(s"Global timeout [${time}] has to be between 0 seconds and 12 hours")
      case _ => Seq()
    }

    treshholdErr ++ localTimeoutErr ++ globalTimeoutErr
  }
}

```




[main/scala/ohnosequences/loquat/configs/autoscaling.scala]: autoscaling.scala.md
[main/scala/ohnosequences/loquat/configs/awsClients.scala]: awsClients.scala.md
[main/scala/ohnosequences/loquat/configs/general.scala]: general.scala.md
[main/scala/ohnosequences/loquat/configs/loquat.scala]: loquat.scala.md
[main/scala/ohnosequences/loquat/configs/resources.scala]: resources.scala.md
[main/scala/ohnosequences/loquat/configs/termination.scala]: termination.scala.md
[main/scala/ohnosequences/loquat/configs/user.scala]: user.scala.md
[main/scala/ohnosequences/loquat/dataMappings.scala]: ../dataMappings.scala.md
[main/scala/ohnosequences/loquat/dataProcessing.scala]: ../dataProcessing.scala.md
[main/scala/ohnosequences/loquat/logger.scala]: ../logger.scala.md
[main/scala/ohnosequences/loquat/loquats.scala]: ../loquats.scala.md
[main/scala/ohnosequences/loquat/manager.scala]: ../manager.scala.md
[main/scala/ohnosequences/loquat/terminator.scala]: ../terminator.scala.md
[main/scala/ohnosequences/loquat/utils.scala]: ../utils.scala.md
[main/scala/ohnosequences/loquat/worker.scala]: ../worker.scala.md
[test/scala/ohnosequences/loquat/test/config.scala]: ../../../../../test/scala/ohnosequences/loquat/test/config.scala.md
[test/scala/ohnosequences/loquat/test/data.scala]: ../../../../../test/scala/ohnosequences/loquat/test/data.scala.md
[test/scala/ohnosequences/loquat/test/dataMappings.scala]: ../../../../../test/scala/ohnosequences/loquat/test/dataMappings.scala.md
[test/scala/ohnosequences/loquat/test/dataProcessing.scala]: ../../../../../test/scala/ohnosequences/loquat/test/dataProcessing.scala.md
[test/scala/ohnosequences/loquat/test/md5.scala]: ../../../../../test/scala/ohnosequences/loquat/test/md5.scala.md