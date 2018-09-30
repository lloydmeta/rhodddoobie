package com.beachape.infra.persistence

import cats.effect.Effect
import com.beachape.config.DBConf
import com.zaxxer.hikari.util.UtilityElf.DefaultThreadFactory
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import doobie.hikari.HikariTransactor

/**
  * Module that deals with Hikari Connection Pool related manipulation
  */
object HikariOps {

  def toTransactor[F[_]: Effect](dbConfig: DBConf): F[HikariTransactor[F]] =
    HikariTransactor.newHikariTransactor[F](driverClassName = dbConfig.driverClassName,
                                            url = dbConfig.jdbcUrl.toString,
                                            user = dbConfig.user,
                                            pass = dbConfig.password)

  def toDataSource(dbConfig: DBConf): HikariDataSource = {
    new HikariDataSource(toHikariConfig(dbConfig))
  }

  private def toHikariConfig(dbConfig: DBConf): HikariConfig = {
    val jConfig = new HikariConfig()
    jConfig.setJdbcUrl(dbConfig.jdbcUrl.toString)
    jConfig.setUsername(dbConfig.user)
    jConfig.setPassword(dbConfig.password)
    jConfig.setDriverClassName(dbConfig.driverClassName)
    jConfig.setThreadFactory(new DefaultThreadFactory("HikariThread", false))
    jConfig
  }
}
