package com.beachape.infra.persistence

import cats.effect.IO
import com.beachape.api.models._
import com.beachape.ids._
import com.beachape.infra.persistence.enums._
import com.beachape.infra.persistence.models.{DeploymentEntity, ResourceEntity}
import com.beachape.infra.persistence.repos._
import doobie.hikari.HikariTransactor
import org.scalatest.{FunSpec, Matchers}
import doobie.scalatest.IOChecker
import helpers.DockerPostgresService

class QuerySoundnessSpec extends FunSpec with Matchers with IOChecker with DockerPostgresService {

  lazy val transactor: HikariTransactor[IO] =
    HikariOps.toTransactor(PostgresDBConfig).unsafeRunSync()

  override def beforeAll(): Unit = {
    super.beforeAll()
    Migration.withConfig(PostgresDBConfig).unsafeRunSync()
    ()
  }

  describe(s"${DeploymentsRepo.getClass.getSimpleName} SQL Queries types") {

    import com.beachape.infra.persistence.repos.DeploymentsRepo.Doobie.Queries

    it("should have the proper types for getting a Deployment") {
      check(Queries.getQuery(DeploymentId(10)))
    }
    it("should have the proper types for getting all Deployments") {
      check(Queries.listQuery)
    }
    it("should have the proper types inserting a new Deployment") {
      check(Queries.insertQuery(DeploymentEntity.CreateWithResources("hello world", Nil)))
    }
    it("should have the proper types inserting a new Resource for a Deployment") {
      check(
        Queries
          .insertResourceQuery(
            DeploymentId(10),
            ResourceKind.Elasticsearch,
            Ref("boom"),
            ResourceEntity.Data(name = "hello world", cpuBoost = true, memLimit = false)))
    }
    it("should have the proper types getting a Resource for a Deployment") {
      check(Queries.getResourceQuery(DeploymentId(10), ResourceKind.APM, Ref("boom")))
    }
    it("should have the proper types updating new Resource for a Deployment") {
      check(
        Queries
          .updateResourceQuery(
            DeploymentId(10),
            ResourceKind.Elasticsearch,
            Ref("boom"),
            ResourceEntity.Data(name = "hello world", cpuBoost = true, memLimit = false)))
    }
    it("should have the proper types deleting a Resource for a Deployment") {
      check(Queries.deleteResourceQuery(DeploymentId(10), ResourceKind.Kibana, Ref("boom")))
    }
    it("should have the proper types deleting a Deployment") {
      check(Queries.deleteQuery(DeploymentId(10)))
    }
    it("should have the proper types updating a Deployment") {
      check(Queries.updateQuery(DeploymentId(10), DeploymentEntity.Data("hi")))
    }

  }

  describe(s"${ResourcesRepo.getClass.getSimpleName} SQL Queries types") {

    import com.beachape.infra.persistence.repos.ResourcesRepo.Doobie.Queries

    it("should have the proper types for getting a Resource") {
      check(Queries.getQuery(ResourceId(10)))
    }
    it("should have the proper types for getting all Resources") {
      check(Queries.listQuery)
    }
    it("should have the proper types deleting a Resource") {
      check(Queries.deleteQuery(ResourceId(10)))
    }

  }

}
