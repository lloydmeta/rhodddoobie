package com.beachape.infra.persistence

import cats.effect.IO
import helpers.DockerPostgresService
import org.scalatest.{FunSpec, Matchers}

class MigrationSpec extends FunSpec with Matchers with DockerPostgresService {

  describe("migrations") {
    it("should run") {
      Migration.withConfig[IO](PostgresDBConfig).unsafeRunSync()
    }
  }

}
