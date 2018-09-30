package com.beachape.infra.persistence.repos

import com.beachape.ids.{DeploymentId, Ref}
import com.beachape.infra.persistence.enums.ResourceKind
import com.beachape.infra.persistence.models.{DeploymentEntity, ResourceEntity}
import helpers.H2DatabaseService
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.{DiagrammedAssertions, FunSpec}
import org.scalatest.EitherValues._
import org.scalatest.OptionValues._

class DeploymentsRepoSpec
    extends FunSpec
    with TypeCheckedTripleEquals
    with DiagrammedAssertions
    with H2DatabaseService {

  describe("Doobie implementation") {

    lazy val deploymentsRepo = DeploymentsRepo.doobie(H2Transactor)

    describe("CRUD") {

      it("return work") {
        val io = for {
          // List
          initList <- deploymentsRepo.list
          _ = assert(initList.isEmpty)
          // Get one, but getting nothing
          impossibleGet <- deploymentsRepo.get(DeploymentId(Long.MaxValue)) // Huge Id
          _ = assert(impossibleGet === None)

          insertedOrErr <- deploymentsRepo.insert(
            DeploymentEntity.CreateWithResources(
              name = "heyo",
              resources = Seq(
                DeploymentEntity.CreateResource(
                  ref = Ref("first"),
                  name = "res1",
                  kind = ResourceKind.Elasticsearch,
                  cpuBoost = true,
                  memLimit = false
                ),
                DeploymentEntity.CreateResource(
                  ref = Ref("second"),
                  name = "res2",
                  kind = ResourceKind.Kibana,
                  cpuBoost = false,
                  memLimit = true
                )
              )
            ))
          Right(inserted) = insertedOrErr

          // Get after insert
          getAfterInsert <- deploymentsRepo.get(inserted.id)
          _ = assert(getAfterInsert.value.name === "heyo")
          _ = assert(getAfterInsert.value.resources.size === 2)
          _ = assert(getAfterInsert.value.resources(0).cpuBoost)
          _ = assert(!getAfterInsert.value.resources(0).memLimit)
          _ = assert(!getAfterInsert.value.resources(1).cpuBoost)
          _ = assert(getAfterInsert.value.resources(1).memLimit)

          // List after insert
          listAfterInsert <- deploymentsRepo.list
          _ = assert(listAfterInsert.exists(_.name === inserted.name))

          _ = assert(inserted.name === "heyo")
          _ = assert(
            inserted.resources.exists(r => r.ref === Ref("first") && r.name === "res1")
          )
          _ = assert(
            inserted.resources.exists(r => r.ref === Ref("second") && r.name === "res2")
          )
          _ = assert(inserted.resources.size === 2)
          _ = assert(inserted === getAfterInsert.value)

          // Update
          updated <- deploymentsRepo.update(inserted.id, DeploymentEntity.Data("the new name"))
          _ = assert(updated.right.value.name === "the new name")
          getAfterUpdate <- deploymentsRepo.get(inserted.id)
          _ = assert(getAfterUpdate.value.name === "the new name")

          /// <-- Testing Resources methods

          // failed get resource
          impossibleGetResource <- deploymentsRepo.getResource(inserted.id,
                                                               ResourceKind.APM,
                                                               Ref("lol"))
          _ = assert(impossibleGetResource.isEmpty)

          // Insert Resource
          insertedOrErr <- deploymentsRepo.insertResource(
            deploymentId = inserted.id,
            kind = ResourceKind.Elasticsearch,
            ref = Ref("sup"),
            data = ResourceEntity.Data(name = "hey", cpuBoost = true, memLimit = false))
          Right(insertedResource) = insertedOrErr

          // Get Resource
          gotten <- deploymentsRepo.getResource(inserted.id,
                                                insertedResource.kind,
                                                insertedResource.ref)
          _ = assert(gotten.value.name === "hey")
          _ = assert(gotten.value.ref === Ref("sup"))
          _ = assert(gotten.value.cpuBoost === true)
          _ = assert(gotten.value.memLimit === false)

          // Update Resource
          updated <- deploymentsRepo.updateResource(
            deploymentId = inserted.id,
            kind = insertedResource.kind,
            ref = insertedResource.ref,
            data = ResourceEntity.Data(name = "new-name", cpuBoost = false, memLimit = true))
          _ = assert(updated.right.value.name === "new-name")
          gottenAfterUpdate <- deploymentsRepo.getResource(inserted.id,
                                                           insertedResource.kind,
                                                           insertedResource.ref)
          _ = assert(gottenAfterUpdate.value.name === "new-name")
          _ = assert(gottenAfterUpdate.value.cpuBoost === false)
          _ = assert(gottenAfterUpdate.value.memLimit === true)

          // Delete Resource
          _ <- deploymentsRepo.deleteResource(inserted.id,
                                              insertedResource.kind,
                                              insertedResource.ref)
          postDeleteResourceGet <- deploymentsRepo.getResource(inserted.id,
                                                               insertedResource.kind,
                                                               insertedResource.ref)
          _ = assert(postDeleteResourceGet.isEmpty)

          ///     Testing Resources methods -->

          // Delete
          deleteCount <- deploymentsRepo.delete(inserted.id)
          _ = assert(deleteCount === 1)
          listAfterDelete <- deploymentsRepo.list
          _ = assert(listAfterDelete.isEmpty)
          getAfterDelete <- deploymentsRepo.get(inserted.id)
          _ = assert(getAfterDelete.isEmpty)
        } yield {
          ()
        }
        io.unsafeRunSync()

      }

    }
  }

}
