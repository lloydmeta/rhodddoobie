package com.beachape.infra.persistence

import helpers.DockerPostgresService
import org.scalatest.{FunSpec, Matchers}

class MigrationSpec extends FunSpec with Matchers with DockerPostgresService {

  describe("migrations") {
    it("should run") {
      Migration.withConfig(PostgresDBConfig).unsafeRunSync()
    }
  }

}
