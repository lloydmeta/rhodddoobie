package com.beachape.app.services
import cats.data.OptionT
import cats.effect.Effect
import cats.implicits._
import com.beachape.api.models.Resource
import com.beachape.ids.ResourceId
import com.beachape.infra.persistence.repos.ResourcesRepo
import io.scalaland.chimney.dsl._

import ResourcesManager._

class ResourcesManager[F[+ _]: Effect](resourcesRepo: ResourcesRepo[F]) {

  def get(resourceId: ResourceId): F[Option[Resource]] =
    OptionT(resourcesRepo.get(resourceId)).map(_.transformInto[Resource]).value

  def list: F[Seq[Resource]] =
    resourcesRepo.list.map(_.transformInto[Seq[Resource]])

  def delete(resourceId: ResourceId): F[Either[NoSuchResource, Unit]] =
    resourcesRepo.delete(resourceId).map {
      case 1 => Right(())
      case _ => Left(NoSuchResource(resourceId))
    }

}

object ResourcesManager {

  sealed trait Error

  final case class NoSuchResource(resourceId: ResourceId) extends Error
}
