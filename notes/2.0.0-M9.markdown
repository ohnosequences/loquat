* #58: Moved data mappings list from config to loquat (to simplify chaining several loquats in a pipeline)
* #57: Fixed termination bugs related to the SQS polling (replaced it with a faster and better checks)
* #61: Manger now sends input tasks in parallel which makes it orders of magnitude faster
* #66: Updated aws-scala-tools to 0.18.1, which led to a lot of internal improvements related to the usage of AWS
* #72: Updated other dependencies
* #42, #60: Added a check that all input/output data-keys have distinct labels
* #65: Loquat config now uses an `S3Folder` for logs instead of just a bucket name (renamed to `logsS3Prefix`)
* #46: Added an email notification in case of manager installation failure
* #21: Added fatal worker failures email notifications

----

See the full list of pull requests merged in this release in the [v2.0-M9 milestone](https://github.com/ohnosequences/loquat/milestone/11?closed=1).
