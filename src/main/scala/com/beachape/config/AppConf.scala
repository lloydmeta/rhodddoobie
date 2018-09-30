package com.beachape.config

import pureconfig.error.ConfigReaderFailures
import pureconfig.module.enumeratum._

final case class AppConf(server: ServerConf, db: DBConf, swagger: SwaggerConf)

object AppConf {

  import com.typesafe.config.ConfigFactory
  import pureconfig.syntax._

  def load(): Either[ConfigReaderFailures, AppConf] = ConfigFactory.load.to[AppConf]

}
