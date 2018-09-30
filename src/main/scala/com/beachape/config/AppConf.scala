package com.beachape.config

import pureconfig.error.ConfigReaderFailures
import pureconfig.module.enumeratum._

/**
  * Our app-level config.
  *
  * PureConfig is used in this project to parse config into this typed object.
  */
final case class AppConf(server: ServerConf, db: DBConf)

object AppConf {

  import com.typesafe.config.ConfigFactory
  import pureconfig.syntax._

  def load(): Either[ConfigReaderFailures, AppConf] = ConfigFactory.load.to[AppConf]

}
