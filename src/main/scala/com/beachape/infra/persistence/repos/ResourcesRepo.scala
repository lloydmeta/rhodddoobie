package com.beachape.infra.persistence.repos

import cats.effect.Effect
import cats.implicits._
import com.beachape.ids._
import com.beachape.infra.persistence.models._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import TypeMappers._

/**
  * Interface for a repository that deals with `ResourceEntity`s
  */
abstract class ResourcesRepo[F[_]: Effect] {

  def get(resourceId: ResourceId): F[Option[ResourceEntity]]

  def list: F[Seq[ResourceEntity]]

  def delete(resourceId: ResourceId): F[Int]

}

object ResourcesRepo {

  /**
    * Given a transactor, returns a Doobie-based implementation of repo algebra
    */
  def doobie[F[_]: Effect](xa: Transactor[F]): ResourcesRepo[F] = new Doobie.Impl[F](xa)

  sealed trait Error
  final case class InsertionError(underlying: Throwable) extends Error

  object Doobie {
    object Queries {

      private[persistence] def getQuery(resourceId: ResourceId): Query0[ResourceEntity] = {
        sql"""
          SELECT r.id, r.deployment_id, r.kind, r.ref, r.name, r.cpu_boost, r.mem_limit
          FROM resources as r
          WHERE r.id = $resourceId
          LIMIT 1
        """.query[ResourceEntity]
      }

      private[persistence] val listQuery: Query0[ResourceEntity] = {
        sql"""
          SELECT r.id, r.deployment_id, r.kind, r.ref, r.name, r.cpu_boost, r.mem_limit
          FROM resources as r
          ORDER By r.id ASC
        """.query[ResourceEntity]
      }

      private[persistence] def deleteQuery(resourceId: ResourceId): Update0 = {
        sql"""
          DELETE FROM resources as r
          WHERE r.id = $resourceId
        """.update
      }

    }

    /**
      * Implementation of our algebra based on Doobie
      */
    class Impl[F[_]: Effect](xa: Transactor[F]) extends ResourcesRepo[F] {

      import Queries._

      def get(resourceId: ResourceId): F[Option[ResourceEntity]] =
        getQuery(resourceId).option.transact(xa)

      def list: F[Seq[ResourceEntity]] = listQuery.to[List].transact(xa).map(_.toSeq)

      def delete(resourceId: ResourceId): F[Int] =
        deleteQuery(resourceId).run.transact(xa)

    }
  }

}
