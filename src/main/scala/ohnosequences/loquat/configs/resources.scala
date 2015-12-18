package ohnosequences.loquat

/* Configuration of resources */
private[loquat]
case class ResourceNames(prefix: String, bucketName: String) {
  /* name of queue with dataMappings */
  val inputQueue: String = prefix + "-loquat-input"
  /* name of topic for dataMappings result notifications */
  val outputQueue: String = prefix + "-loquat-output"
  /* name of queue with errors (will be subscribed to errorTopic) */
  val errorQueue: String = prefix + "-loquat-errors"
  /* name of bucket for logs files */
  val bucket: String = bucketName
  /* topic name to notificate user about termination of loquat */
  val notificationTopic: String = prefix + "-loquat-notifications"
  /* name of the manager autoscaling group */
  val managerGroup: String = prefix + "-loquat-manager"
  /* name of the workers autoscaling group */
  val workersGroup: String = prefix + "-loquat-workers"
}
