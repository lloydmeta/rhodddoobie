package com.beachape.infra.persistence

import cats.effect.IO
import com.beachape.config.DBConf
import org.flywaydb.core.Flyway

import scala.util.Try

object Migration {

  final case class Success(migrationsRun: Int)    extends AnyVal
  final case class Failure(underlying: Throwable) extends AnyVal

  def withConfig(dbConfig: DBConf): IO[Either[Failure, Success]] = IO {
    if (dbConfig.autoMigrate) {
      Try {
        val dataSource = HikariOps.toDataSource(dbConfig)
        val flyway     = new Flyway()
        flyway.setDataSource(dataSource)
        val migrationsRun = flyway.migrate()
        dataSource.close()
        Success(migrationsRun)
      }.toEither.left.map(Failure.apply)
    } else {
      Right(Success(0))
    }
  }

}
