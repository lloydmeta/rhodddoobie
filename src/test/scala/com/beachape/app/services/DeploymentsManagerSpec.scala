package com.beachape.app.services
import cats.effect.IO
import com.beachape.api.models.{Deployment, Resource}
import com.beachape.api.enums.{ResourceKind => ApiResourceKind}
import com.beachape.app.services.DeploymentsManager.NonUniqueResourcesDetected
import com.beachape.ids.{DeploymentId, Ref, ResourceId}
import com.beachape.infra.persistence.enums.ResourceKind
import com.beachape.infra.persistence.models.{DeploymentEntity, ResourceEntity}
import com.beachape.infra.persistence.repos.DeploymentsRepo
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest._
import io.scalaland.chimney.dsl._

class DeploymentsManagerSpec
    extends FunSpec
    with DiagrammedAssertions
    with TypeCheckedTripleEquals
    with Inside
    with OptionValues
    with EitherValues {

  describe("#create") {

    it(
      s"should return ${NonUniqueResourcesDetected.getClass.getSimpleName} if there are resources in the payload that have the same Kind and Ref") {
      val payload = Deployment.DeploymentDataWithResources(
        name = "shouldfail",
        resources = Seq(
          Deployment.ResourceInDeploymentData(
            ref = Ref("es"),
            kind = ApiResourceKind.Elasticsearch,
            name = "es1",
            cpuBoost = true,
            memLimit = true
          ),
          Deployment.ResourceInDeploymentData(
            ref = Ref("es"),
            kind = ApiResourceKind.Elasticsearch,
            name = "es2",
            cpuBoost = true,
            memLimit = true
          ),
          Deployment.ResourceInDeploymentData(
            ref = Ref("kib"),
            kind = ApiResourceKind.Kibana,
            name = "kib",
            cpuBoost = true,
            memLimit = true
          )
        )
      )
      inside(subject.create(payload).unsafeRunSync()) {
        case Left(e: NonUniqueResourcesDetected) =>
          assert(e.repeats.contains((ApiResourceKind.Elasticsearch, Ref("es"))))
      }

    }

    it("should otherwise work") {
      val payload = Deployment.DeploymentDataWithResources(
        name = "shouldfail",
        resources = Seq(
          Deployment.ResourceInDeploymentData(
            ref = Ref("es1"),
            kind = ApiResourceKind.Elasticsearch,
            name = "es1",
            cpuBoost = true,
            memLimit = true
          ),
          Deployment.ResourceInDeploymentData(
            ref = Ref("es2"),
            kind = ApiResourceKind.Elasticsearch,
            name = "es2",
            cpuBoost = true,
            memLimit = true
          ),
          Deployment.ResourceInDeploymentData(
            ref = Ref("kib"),
            kind = ApiResourceKind.Kibana,
            name = "kib",
            cpuBoost = true,
            memLimit = true
          )
        )
      )
      assert(subject.create(payload).unsafeRunSync().isRight)
    }

  }

  describe("#list") {

    it("should delegate to the repo") {
      val r        = subject.list.unsafeRunSync()
      val expected = Seq(existingDeploymentEntity).transformInto[Seq[Deployment]]
      assert(r === expected)
    }

  }

  describe("#get") {

    it("should delegate to the repo") {
      val prg = for {
        r1 <- subject.get(DeploymentId(Long.MaxValue))
        r2 <- subject.get(existingDeploymentId)
      } yield {
        assert(r1.isEmpty)
        assert(r2.isDefined)
      }
      prg.unsafeRunSync()
    }

  }

  describe("#update") {

    it("should return Left if the id is bogus") {
      val r =
        subject.update(DeploymentId(Long.MaxValue), Deployment.DeploymentData("hi")).unsafeRunSync()
      assert(r.isLeft)
    }

    it("should return Right if the id exists") {
      val r = subject.update(existingDeploymentId, Deployment.DeploymentData("hi")).unsafeRunSync()
      assert(r.isRight)
    }

  }

  describe("#delete") {

    it("should return Left if the id is bogus") {
      val r =
        subject.delete(DeploymentId(Long.MaxValue)).unsafeRunSync()
      assert(r.isLeft)
    }

    it("should return Right if the id exists") {
      val r = subject.delete(existingDeploymentId).unsafeRunSync()
      assert(r.isRight)
    }

  }

  describe("#addResource") {

    val data = Resource.ResourceData(name = "res", cpuBoost = true, memLimit = true)

    it("should return Left if the deployment id does not exist") {
      val r = subject
        .addResource(DeploymentId(Long.MaxValue), ApiResourceKind.Elasticsearch, Ref("r"), data)
        .unsafeRunSync()
      assert(r.isLeft)
    }

    it(
      "should return Left if the deployment id exists and there is already a Resource on the Deployment with the same Kind and Ref") {
      val r = subject
        .addResource(existingDeploymentId, ApiResourceKind.Elasticsearch, Ref("es"), data)
        .unsafeRunSync()
      assert(r.isLeft)
    }

    it("should return Right otherwise") {
      val r = subject
        .addResource(existingDeploymentId, ApiResourceKind.Elasticsearch, Ref("es3"), data)
        .unsafeRunSync()
      assert(r.isRight)
    }

  }

  describe("#updateResource") {

    val data = Resource.ResourceData(name = "res", cpuBoost = true, memLimit = true)

    it("should return Left if the deployment id does not exist") {
      val r = subject
        .updateResource(DeploymentId(Long.MaxValue), ApiResourceKind.Elasticsearch, Ref("r"), data)
        .unsafeRunSync()
      assert(r.isLeft)
    }

    it(
      "should return Left if the deployment id exists and there is no Resource on the Deployment with the same Kind and Ref") {
      val r = subject
        .updateResource(existingDeploymentId, ApiResourceKind.Elasticsearch, Ref("es3"), data)
        .unsafeRunSync()
      assert(r.isLeft)
    }

    it("should return Right otherwise") {
      val r = subject
        .updateResource(existingDeploymentId, ApiResourceKind.Elasticsearch, Ref("es"), data)
        .unsafeRunSync()
      assert(r.isRight)
    }

  }

  describe("#deleteResource") {

    it("should return Left if the deployment id does not exist") {
      val r = subject
        .deleteResource(DeploymentId(Long.MaxValue), ApiResourceKind.Elasticsearch, Ref("r"))
        .unsafeRunSync()
      assert(r.isLeft)
    }

    it(
      "should return Left if the deployment id exists and there is no Resource on the Deployment with the same Kind and Ref") {
      val r = subject
        .deleteResource(existingDeploymentId, ApiResourceKind.Elasticsearch, Ref("es3"))
        .unsafeRunSync()
      assert(r.isLeft)
    }

    it("should return Right otherwise") {
      val r = subject
        .deleteResource(existingDeploymentId, ApiResourceKind.Elasticsearch, Ref("es"))
        .unsafeRunSync()
      assert(r.isRight)
    }

  }

  private val existingDeploymentId = DeploymentId(1)

  private val existingDeploymentEntity = DeploymentEntity(
    id = existingDeploymentId,
    name = "dep",
    resources = Seq(
      ResourceEntity(
        id = ResourceId(1),
        deploymentId = existingDeploymentId,
        kind = ResourceKind.Elasticsearch,
        ref = Ref("es"),
        name = "app-es",
        cpuBoost = true,
        memLimit = true
      ),
      ResourceEntity(
        id = ResourceId(1),
        deploymentId = existingDeploymentId,
        kind = ResourceKind.Kibana,
        ref = Ref("kib"),
        name = "app-kib",
        cpuBoost = true,
        memLimit = true
      ),
      ResourceEntity(
        id = ResourceId(1),
        deploymentId = existingDeploymentId,
        kind = ResourceKind.APM,
        ref = Ref("apm"),
        name = "app-apm",
        cpuBoost = true,
        memLimit = true
      )
    )
  )

  private val deploymentsRepo = new DeploymentsRepo[IO] {
    def get(deploymentId: DeploymentId): IO[Option[DeploymentEntity]] = IO.pure {
      if (deploymentId === existingDeploymentId)
        Some(existingDeploymentEntity)
      else None
    }
    def list: IO[Seq[DeploymentEntity]] = IO.pure(Seq(existingDeploymentEntity))
    def insert(deployment: DeploymentEntity.CreateWithResources)
      : IO[Either[DeploymentsRepo.ResourceKindRefViolation, DeploymentEntity]] =
      IO.pure(Right(existingDeploymentEntity))
    def delete(deploymentId: DeploymentId): IO[Int] = IO.pure {
      if (deploymentId === existingDeploymentId)
        1
      else
        0
    }
    def update(id: DeploymentId, deployment: DeploymentEntity.Data)
      : IO[Either[DeploymentsRepo.NoSuchDeploymentError, DeploymentEntity]] =
      IO.pure {
        if (id === existingDeploymentId)
          Right(existingDeploymentEntity)
        else
          Left(DeploymentsRepo.NoSuchDeploymentError(id))
      }
    def insertResource(deploymentId: DeploymentId,
                       kind: ResourceKind,
                       ref: Ref,
                       data: ResourceEntity.Data)
      : IO[Either[DeploymentsRepo.InsertResourceError, ResourceEntity]] =
      IO.pure {
        if (deploymentId === existingDeploymentId)
          Right(existingDeploymentEntity.resources.head)
        else
          Left(DeploymentsRepo.NoSuchDeploymentError(deploymentId))

      }
    def getResource(deploymentId: DeploymentId,
                    kind: ResourceKind,
                    ref: Ref): IO[Option[ResourceEntity]] =
      IO.pure {
        if (deploymentId === existingDeploymentId)
          existingDeploymentEntity.resources.find(r => r.kind === kind && r.ref === ref)
        else
          None
      }
    def updateResource(
        deploymentId: DeploymentId,
        kind: ResourceKind,
        ref: Ref,
        data: ResourceEntity.Data): IO[Either[DeploymentsRepo.NoSuchResource, ResourceEntity]] =
      IO.pure {
        if (deploymentId === existingDeploymentId && existingDeploymentEntity.resources.exists(
              r => r.kind === kind && r.ref === ref))
          Right(existingDeploymentEntity.resources.head)
        else
          Left(DeploymentsRepo.NoSuchResource(deploymentId, kind, ref))
      }
    def deleteResource(deploymentId: DeploymentId, kind: ResourceKind, ref: Ref): IO[Int] =
      IO.pure {
        if (existingDeploymentEntity.resources.exists(r => r.kind === kind && r.ref === ref))
          1
        else
          0
      }
  }

  private val subject = new DeploymentsManager(deploymentsRepo)
}
