package ohnosequences.loquat

import com.typesafe.scalalogging.LazyLogging

import ohnosequences.awstools.AWSClients


/* Any config here can validate itself (in runtime) */
trait AnyConfig extends LazyLogging {

  val configLabel: String

  def validationErrors(aws: AWSClients): Seq[String]

  /* Config dependencies */
  val subConfigs: Seq[AnyConfig]

  /* This method validates subconfigs and logs validation errors */
  final def validateWithLogging(aws: AWSClients): Seq[String] = {
    val subErrors = subConfigs.flatMap{ _.validateWithLogging(aws) }

    val errors = validationErrors(aws)
    errors.foreach{ msg => logger.error(msg) }

    if (errors.isEmpty) logger.debug(s"Validated ${configLabel}")

    subErrors ++ errors
  }
}

abstract class Config(val configLabel: String)(val subConfigs: AnyConfig*) extends AnyConfig
