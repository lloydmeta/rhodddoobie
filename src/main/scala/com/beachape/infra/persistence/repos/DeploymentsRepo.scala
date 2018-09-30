package com.beachape.infra.persistence.repos

import cats.effect.{Async, Effect}
import cats.implicits._
import com.beachape.ids._
import com.beachape.infra.persistence.models._
import com.beachape.infra.persistence.enums._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import doobie.postgres._
import io.scalaland.chimney.dsl._
import TypeMappers._
import cats.{Monad, Traverse}

/**
  * Interface for a repository that deals with `DeploymentEntity`s
  */
abstract class DeploymentsRepo[F[_]: Effect] {

  import DeploymentsRepo._

  def get(deploymentId: DeploymentId): F[Option[DeploymentEntity]]

  def list: F[Seq[DeploymentEntity]]

  def insert(deployment: DeploymentEntity.CreateWithResources)
    : F[Either[ResourceKindRefViolation, DeploymentEntity]]

  def delete(deploymentId: DeploymentId): F[Int]

  def update(id: DeploymentId,
             deployment: DeploymentEntity.Data): F[Either[NoSuchDeploymentError, DeploymentEntity]]

  def insertResource(deploymentId: DeploymentId,
                     kind: ResourceKind,
                     ref: Ref,
                     data: ResourceEntity.Data): F[Either[InsertResourceError, ResourceEntity]]

  def getResource(deploymentId: DeploymentId,
                  kind: ResourceKind,
                  ref: Ref): F[Option[ResourceEntity]]

  def updateResource(deploymentId: DeploymentId,
                     kind: ResourceKind,
                     ref: Ref,
                     data: ResourceEntity.Data): F[Either[NoSuchResource, ResourceEntity]]

  def deleteResource(deploymentId: DeploymentId, kind: ResourceKind, ref: Ref): F[Int]

}

object DeploymentsRepo {

  /**
    * Given a transactor, returns a Doobie-based implementation of repo algebra
    */
  def doobie[F[_]: Effect](xa: Transactor[F]): DeploymentsRepo[F] = new Doobie.Impl[F](xa)

  sealed trait Error

  sealed trait InsertResourceError extends Error

  final case class ResourceKindRefViolation(message: String)         extends InsertResourceError
  final case class NoSuchDeploymentError(deploymentId: DeploymentId) extends InsertResourceError

  final case class NoSuchResource(deploymentId: DeploymentId, kind: ResourceKind, ref: Ref)
      extends Error

  object Doobie {

    /**
      * Holds various SQL queries that power our Doobie implementation of [[DeploymentsRepo]]
      */
    object Queries {

      private[persistence] def getQuery(
          deploymentId: DeploymentId): Query0[DeploymentJoinedWithResourceRow] = {
        sql"""
          SELECT d.id, d.name, r.id, r.kind, r.ref, r.name, r.cpu_boost, r.mem_limit
          FROM
            deployments AS d
            LEFT JOIN resources AS r ON d.id = r.deployment_id
          WHERE d.id = $deploymentId
          ORDER BY d.id, r.id, r.ref ASC
        """.query[DeploymentJoinedWithResourceRow]
      }

      private[persistence] val listQuery: Query0[DeploymentJoinedWithResourceRow] = {
        sql"""
           SELECT d.id, d.name, r.id, r.kind, r.ref, r.name, r.cpu_boost, r.mem_limit
           FROM
             deployments AS d
           LEFT JOIN resources AS r ON d.id = r.deployment_id
           ORDER BY d.id, r.id, r.ref ASC
        """.query[DeploymentJoinedWithResourceRow]
      }

      private[persistence] def insertQuery(
          newDeployment: DeploymentEntity.CreateWithResources): Update0 = {
        sql"""
           INSERT
           INTO deployments (name)
           VALUES (${newDeployment.name})
           """.update
      }

      private[persistence] def insertResourceQuery(deploymentId: DeploymentId,
                                                   kind: ResourceKind,
                                                   ref: Ref,
                                                   data: ResourceEntity.Data): Update0 = {
        sql"""
           INSERT
           INTO resources (deployment_id, kind, ref, name, cpu_boost, mem_limit)
           VALUES ($deploymentId, $kind, $ref, ${data.name}, ${data.cpuBoost}, ${data.memLimit})
           """.update
      }

      private[persistence] def getResourceQuery(deploymentId: DeploymentId,
                                                kind: ResourceKind,
                                                ref: Ref): Query0[ResourceEntity] = {
        sql"""
           SELECT r.id, r.deployment_id, r.kind, r.ref, r.name, r.cpu_boost, r.mem_limit
           FROM resources AS r
           WHERE r.deployment_id = $deploymentId AND
                 r.kind = $kind AND
                 r.ref = $ref
           LIMIT 1
           """.query[ResourceEntity]
      }

      private[persistence] def updateResourceQuery(deploymentId: DeploymentId,
                                                   kind: ResourceKind,
                                                   ref: Ref,
                                                   data: ResourceEntity.Data): Update0 = {
        sql"""
           UPDATE resources
           SET
            name = ${data.name},
            cpu_boost = ${data.cpuBoost},
            mem_limit = ${data.memLimit}
           WHERE
             deployment_id = $deploymentId AND
             kind = $kind AND
             ref = $ref
           """.update
      }

      private[persistence] def deleteResourceQuery(deploymentId: DeploymentId,
                                                   kind: ResourceKind,
                                                   ref: Ref): Update0 = {
        sql"""
           DELETE FROM resources AS r
           WHERE
             r.deployment_id = $deploymentId AND
             r.kind = $kind AND
             r.ref = $ref
           """.update
      }

      private[persistence] def deleteQuery(deploymentId: DeploymentId): Update0 = {
        sql"""
          DELETE FROM deployments as d
          WHERE d.id = $deploymentId
        """.update
      }

      private[persistence] def updateQuery(id: DeploymentId,
                                           data: DeploymentEntity.Data): Update0 = {
        sql"""
          UPDATE deployments
          SET name = ${data.name}
          WHERE id = $id
        """.update
      }

      private[persistence] final case class DeploymentJoinedWithResourceRow(
          deploymentId: DeploymentId,
          deploymentName: String,
          resourceId: Option[ResourceId],
          resourceKind: Option[ResourceKind],
          resourceRef: Option[Ref],
          resourceName: Option[String],
          cpuBoost: Option[Boolean],
          memLimit: Option[Boolean]
      ) {

        def resource: Option[ResourceEntity] =
          for {
            rId    <- resourceId
            kind   <- resourceKind
            ref    <- resourceRef
            name   <- resourceName
            cBoost <- cpuBoost
            mLimit <- memLimit
          } yield
            ResourceEntity(
              id = rId,
              deploymentId = deploymentId,
              kind = kind,
              ref = ref,
              name = name,
              cpuBoost = cBoost,
              memLimit = mLimit
            )
      }
    }

    /**
      * Implementation of our algebra based on Doobie
      */
    class Impl[F[_]: Effect](xa: Transactor[F]) extends DeploymentsRepo[F] {

      import Queries._

      def get(id: DeploymentId): F[Option[DeploymentEntity]] =
        getQuery(id).to[List].map(deploymentWithResourceRowsToEntities(_).headOption).transact(xa)

      private def deploymentWithResourceRowsToEntities(
          rows: Seq[DeploymentJoinedWithResourceRow]): Seq[DeploymentEntity] = {
        rows
          .groupBy(row => (row.deploymentId, row.deploymentName))
          .map {
            case ((deploymentId, deploymentName), deploymentJoinedWithResourceRows) =>
              DeploymentEntity(deploymentId,
                               deploymentName,
                               deploymentJoinedWithResourceRows.flatMap(_.resource))
          }
          .toSeq
      }

      def list: F[Seq[DeploymentEntity]] =
        listQuery.to[List].transact(xa).map(deploymentWithResourceRowsToEntities)

      @SuppressWarnings(Array("org.wartremover.warts.Any"))
      def insert(data: DeploymentEntity.CreateWithResources)
        : F[Either[ResourceKindRefViolation, DeploymentEntity]] = {

        val resources = data.resources.toList

        val connectionIO: ConnectionIO[Either[ResourceKindRefViolation, DeploymentEntity]] = for {
          deploymentId <- insertQuery(data).withUniqueGeneratedKeys[DeploymentId]("id")
          // Usage of H2 prevents us from using `updateManyWithGeneratedKeys` so we ghetto traverse it like gangstas
          resources <- Traverse[List]
            .traverse[ConnectionIO, DeploymentEntity.CreateResource, ResourceEntity](resources) {
              resource =>
                insertResourceQuery(deploymentId,
                                    resource.kind,
                                    resource.ref,
                                    resource.transformInto[ResourceEntity.Data])
                  .withUniqueGeneratedKeys[ResourceId]("id")
                  .map { resourceId =>
                    resource
                      .into[ResourceEntity]
                      .withFieldConst(_.id, resourceId)
                      .withFieldConst(_.deploymentId, deploymentId)
                      .transform
                  }
            }
        } yield
          Either.right[ResourceKindRefViolation, DeploymentEntity](
            data
              .into[DeploymentEntity]
              .withFieldConst(_.id, deploymentId)
              .withFieldConst(_.resources, resources)
              .transform
          )

        connectionIO
          .exceptSomeSqlState {
            case sqlstate.class23.UNIQUE_VIOLATION =>
              Either
                .left[ResourceKindRefViolation, DeploymentEntity](
                  ResourceKindRefViolation("One or more of references have the same Kind and Ref."))
                .pure[ConnectionIO]
          }
          .transact(xa)
      }

      def delete(deploymentId: DeploymentId): F[Int] =
        deleteQuery(deploymentId).run.transact(xa)

      def update(
          id: DeploymentId,
          data: DeploymentEntity.Data): F[Either[NoSuchDeploymentError, DeploymentEntity]] = {
        val io = for {
          updated <- updateQuery(id, data).run
          fetched <- if (updated == 1)
            getQuery(id).to[List].map(deploymentWithResourceRowsToEntities(_).headOption)
          else
            // Rollback if we didn't update exactly one; you never know what might have happened
            HC.rollback *> Option.empty[DeploymentEntity].pure[ConnectionIO]
          r = fetched match {
            case Some(deployment) =>
              Either.right[NoSuchDeploymentError, DeploymentEntity](deployment)
            case None =>
              Either.left[NoSuchDeploymentError, DeploymentEntity](NoSuchDeploymentError(id))
          }
        } yield r
        io.transact(xa)
      }

      @SuppressWarnings(Array("org.wartremover.warts.Any"))
      def insertResource(
          deploymentId: DeploymentId,
          kind: ResourceKind,
          ref: Ref,
          data: ResourceEntity.Data): F[Either[InsertResourceError, ResourceEntity]] = {
        val io: ConnectionIO[Either[InsertResourceError, ResourceEntity]] =
          insertResourceQuery(deploymentId, kind, ref, data)
            .withUniqueGeneratedKeys[ResourceId]("id")
            .map(
              id =>
                Either.right[InsertResourceError, ResourceEntity](
                  data
                    .into[ResourceEntity]
                    .withFieldConst(_.id, id)
                    .withFieldConst(_.deploymentId, deploymentId)
                    .withFieldConst(_.ref, ref)
                    .withFieldConst(_.kind, kind)
                    .transform
              ))
        io.exceptSomeSqlState {
            case sqlstate.class23.FOREIGN_KEY_VIOLATION =>
              Either
                .left[InsertResourceError, ResourceEntity](
                  NoSuchDeploymentError(deploymentId)
                )
                .pure[ConnectionIO]
            case sqlstate.class23.UNIQUE_VIOLATION =>
              Either
                .left[InsertResourceError, ResourceEntity](ResourceKindRefViolation(
                  s"A Resource with [$kind] [$ref] already exists for Deployment [$deploymentId]"))
                .pure[ConnectionIO]
          }
          .transact(xa)
      }

      def getResource(deploymentId: DeploymentId,
                      kind: ResourceKind,
                      ref: Ref): F[Option[ResourceEntity]] =
        getResourceQuery(deploymentId, kind, ref).option.transact(xa)

      def updateResource(deploymentId: DeploymentId,
                         kind: ResourceKind,
                         ref: Ref,
                         data: ResourceEntity.Data): F[Either[NoSuchResource, ResourceEntity]] = {
        val q = for {
          updated <- updateResourceQuery(deploymentId, kind, ref, data).run // can't use RETURNING and still have H2 work...
          retrieved <- if (updated == 1)
            getResourceQuery(deploymentId, kind, ref).option
          else // If we did not update exactly one, use the escape hatch
            HC.rollback *> Option.empty[ResourceEntity].pure[ConnectionIO]
          r <- Monad[ConnectionIO].pure {
            retrieved match {
              case Some(r) => Either.right[NoSuchResource, ResourceEntity](r)
              case None =>
                Either.left[NoSuchResource, ResourceEntity](NoSuchResource(deploymentId, kind, ref))
            }
          }
        } yield r

        Async.shift[F](scala.concurrent.ExecutionContext.global) *> q.transact(xa)
      }

      def deleteResource(deploymentId: DeploymentId, kind: ResourceKind, ref: Ref): F[Int] =
        deleteResourceQuery(deploymentId, kind, ref).run.transact(xa)
    }

  }

}
