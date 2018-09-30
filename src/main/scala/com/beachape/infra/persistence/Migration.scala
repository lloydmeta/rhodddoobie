package com.beachape.infra.persistence

import cats.effect.Effect
import com.beachape.config.DBConf
import org.flywaydb.core.Flyway

object Migration {

  final case class Result(migrationsRun: Int) extends AnyVal

  /**
    * Given a `DBConf`, if configured to do so, attempts to auto-run migrations on the database
    */
  def withConfig[F[_]: Effect](dbConfig: DBConf): F[Result] = Effect[F].delay {
    if (dbConfig.autoMigrate) {
      val dataSource = HikariOps.toDataSource(dbConfig)
      try {
        val flyway = new Flyway()
        flyway.setDataSource(dataSource)
        val migrationsRun = flyway.migrate()
        Result(migrationsRun)
      } finally {
        dataSource.close()
      }
    } else {
      Result(0)
    }
  }

}
