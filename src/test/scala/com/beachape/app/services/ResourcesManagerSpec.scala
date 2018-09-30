package com.beachape.app.services

import cats.effect.IO
import com.beachape.api.models.Resource
import com.beachape.app.services.ResourcesManager.NoSuchResource
import com.beachape.ids.{DeploymentId, Ref, ResourceId}
import com.beachape.infra.persistence.enums.ResourceKind
import com.beachape.infra.persistence.models.ResourceEntity
import com.beachape.infra.persistence.repos.ResourcesRepo
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.{DiagrammedAssertions, EitherValues, FunSpec, OptionValues}
import io.scalaland.chimney.dsl._

class ResourcesManagerSpec
    extends FunSpec
    with DiagrammedAssertions
    with TypeCheckedTripleEquals
    with OptionValues
    with EitherValues {

  describe("#get") {

    it("should return None if the id provided is not in the repo") {
      val r = subject.get(ResourceId(Long.MaxValue)).unsafeRunSync()
      assert(r.isEmpty)
    }

    it("should return a transformed entity if the id provided is in the repo") {
      val r        = subject.get(existingId).unsafeRunSync().value
      val expected = resourceEntity.transformInto[Resource]
      assert(r === expected)
    }
  }

  describe("#list") {

    it("should return a list of Resources") {
      val r        = subject.list.unsafeRunSync()
      val expected = Seq(resourceEntity).transformInto[Seq[Resource]]
      assert(r === expected)
    }
  }

  describe("#delete") {

    it(s"should return Left if the id provided does not exist") {
      val r = subject.delete(ResourceId(Long.MaxValue)).unsafeRunSync()
      assert(r.isLeft)
    }
    it(s"should return Right if the id provided does exsit") {
      val r = subject.delete(existingId).unsafeRunSync()
      assert(r.isRight)
    }

  }

  private val existingId = ResourceId(1)
  private val resourceEntity = ResourceEntity(
    id = existingId,
    deploymentId = DeploymentId(1),
    kind = ResourceKind.APM,
    ref = Ref("apm2"),
    name = "my-apm",
    cpuBoost = true,
    memLimit = true
  )

  private val resourceRepo = new ResourcesRepo[IO] {
    def get(resourceId: ResourceId): IO[Option[ResourceEntity]] = IO.pure {
      if (resourceId === existingId) Some(resourceEntity) else None
    }
    def list: IO[Seq[ResourceEntity]] = IO.pure(Seq(resourceEntity))
    def delete(resourceId: ResourceId): IO[Int] = IO.pure {
      if (resourceId === existingId) 1 else 0
    }
  }

  private val subject = new ResourcesManager(resourceRepo)

}
