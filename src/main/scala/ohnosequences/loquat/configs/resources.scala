package ohnosequences.loquat

import ohnosequences.awstools.s3.S3Folder

/* Configuration of resources */
private[loquat]
case class ResourceNames(prefix: String, logsS3Prefix: S3Folder) {
  /* name of queue with dataMappings */
  val inputQueue: String = prefix + "-loquat-input"
  /* name of topic for dataMappings result notifications */
  val outputQueue: String = prefix + "-loquat-output"
  /* name of queue with errors (will be subscribed to errorTopic) */
  val errorQueue: String = prefix + "-loquat-errors"
  /* name of bucket for logs files */
  val logs: S3Folder = logsS3Prefix / prefix /
  /* topic name to notify user about termination of loquat */
  val notificationTopic: String = prefix + "-loquat-notifications"
  /* name of the manager autoscaling group */
  val managerGroup: String = prefix + "-loquat-manager"
  val managerLaunchConfig: String = managerGroup + "-launch-configuration"
  /* name of the workers autoscaling group */
  val workersGroup: String = prefix + "-loquat-workers"
  val workersLaunchConfig: String = workersGroup + "-launch-configuration"
}
