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


/* Configuration of termination conditions */
case class TerminationConfig(
  // if true loquat will terminate after solving all initial tasks
  terminateAfterInitialDataMappings: Boolean,
  // if true loquat will terminate after errorQueue will contain more unique messages then threshold
  errorsThreshold: Option[Int],
  // maximum time for processing one task
  taskProcessingTimeout: Option[FiniteDuration],
  // maximum time for everything
  globalTimeout: Option[FiniteDuration]
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

case object TerminationConfig {

  def default(): TerminationConfig = TerminationConfig(
    terminateAfterInitialDataMappings = true,
    errorsThreshold = None,
    taskProcessingTimeout = None,
    globalTimeout = None
  )
}
