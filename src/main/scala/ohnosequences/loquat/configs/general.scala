package ohnosequences.loquat

import com.typesafe.scalalogging.LazyLogging


/* Any config here can validate itself (in runtime) */
trait AnyConfig extends LazyLogging {

  lazy private val label: String = this.toString

  def validationErrors: Seq[String]

  /* Config dependencies */
  val subConfigs: Seq[AnyConfig]

  /* This method validates subconfigs and logs validation errors */
  final def validate: Seq[String] = {
    val subErrors = subConfigs.flatMap{ _.validate }

    val errors = validationErrors
    errors.foreach{ msg => logger.error(msg) }

    if (errors.isEmpty) logger.debug(s"Validated [${label}] config")

    subErrors ++ errors
  }
}

abstract class Config(val subConfigs: AnyConfig*) extends AnyConfig
