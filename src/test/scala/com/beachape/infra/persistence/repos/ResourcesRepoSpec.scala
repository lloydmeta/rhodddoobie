package com.beachape.infra.persistence.repos

import com.beachape.ids._
import com.beachape.infra.persistence.models._
import com.beachape.infra.persistence.enums._
import helpers.H2DatabaseService
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.{DiagrammedAssertions, FunSpec}

class ResourcesRepoSpec
    extends FunSpec
    with TypeCheckedTripleEquals
    with DiagrammedAssertions
    with H2DatabaseService {

  describe("Doobie implementation") {

    lazy val deploymentsRepo = DeploymentsRepo.doobie(H2Transactor)
    lazy val resourcesRepo   = ResourcesRepo.doobie(H2Transactor)

    describe("CRUD") {

      it("return work") {
        val io = for {
          initList <- resourcesRepo.list
          _ = assert(initList.isEmpty)
          impossibleGet <- resourcesRepo.get(ResourceId(Long.MaxValue)) // Huge Id
          _ = assert(impossibleGet.isEmpty)

          insertedDeploymentOrErr <- deploymentsRepo.insert(
            DeploymentEntity.CreateWithResources(name = "heyo", resources = Nil))
          Right(insertedDeployment) = insertedDeploymentOrErr

          insertedOrErr <- deploymentsRepo.insertResource(
            deploymentId = insertedDeployment.id,
            kind = ResourceKind.Elasticsearch,
            ref = Ref("hi"),
            data = ResourceEntity.Data(name = "hi", cpuBoost = true, memLimit = false))
          Right(inserted) = insertedOrErr

          listAfterInsert <- resourcesRepo.list
          _ = assert(listAfterInsert.contains(inserted))

          deleteCount <- resourcesRepo.delete(inserted.id)
          _ = assert(deleteCount === 1)
          listAfterDelete <- resourcesRepo.list
          _ = assert(listAfterDelete.isEmpty)
          getAfterDelete <- resourcesRepo.get(inserted.id)
          _ = assert(getAfterDelete.isEmpty)

          // assert cascading delete
          insertedDeploymentOrErr <- deploymentsRepo.insert(
            DeploymentEntity.CreateWithResources(name = "heyo", resources = Nil))
          Right(insertedDeployment) = insertedDeploymentOrErr

          insertedOrErr <- deploymentsRepo.insertResource(
            deploymentId = insertedDeployment.id,
            kind = ResourceKind.Elasticsearch,
            ref = Ref("hi"),
            data = ResourceEntity.Data(name = "hi", cpuBoost = true, memLimit = false))
          Right(inserted) = insertedOrErr
          _              <- deploymentsRepo.delete(insertedDeployment.id)
          getAfterDelete <- resourcesRepo.get(inserted.id)
          _ = assert(getAfterDelete.isEmpty)
        } yield {
          ()
        }
        io.unsafeRunSync()

      }

    }
  }

}
