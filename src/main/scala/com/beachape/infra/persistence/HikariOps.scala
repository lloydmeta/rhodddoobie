package com.beachape.infra.persistence

import cats.effect.IO
import com.beachape.config.DBConf
import com.zaxxer.hikari.util.UtilityElf.DefaultThreadFactory
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import doobie.hikari.HikariTransactor

object HikariOps {

  def toTransactor(dbConfig: DBConf): IO[HikariTransactor[IO]] =
    HikariTransactor.newHikariTransactor[IO](driverClassName = dbConfig.driverClassName,
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
