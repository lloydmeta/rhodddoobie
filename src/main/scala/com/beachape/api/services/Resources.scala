package com.beachape.api.services

import cats.Monad
import cats.effect.Effect
import cats.syntax.all._
import com.beachape.api.models._
import org.http4s.rho.RhoService
import org.http4s.rho.swagger.SwaggerSyntax
import io.circe.generic.auto._
import com.beachape.api.serdes.Encoders._
import com.beachape.api.serdes.Parsers
import com.beachape.app.services.ResourcesManager
import com.beachape.app.services.ResourcesManager.NoSuchResource
import com.beachape.ids._

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements", "org.wartremover.warts.Any"))
class Resources[F[+ _]: Effect: Monad](resourcesManager: ResourcesManager[F],
                                       swaggerSyntax: SwaggerSyntax[F])
    extends RhoService[F]
    with Parsers[F] {

  import swaggerSyntax._

  private val resources = "resources"

  private val resourceIdVar = pathVar[ResourceId]("resource_id", "The id of a Resource.")

  "Get all the Resources that have been persisted" **
    GET / resources |>> resourcesManager.list.flatMap(Ok(_))

  "Get a single Resource by id" **
    GET / resources / resourceIdVar |>> { resourceId: ResourceId =>
    resourcesManager.get(resourceId).flatMap {
      case Some(resource) => Ok(resource)
      case None           => NotFound(Error(s"No resource with id $resourceId"))
    }
  }

  "Delete a Resource" **
    DELETE / resources / resourceIdVar |>> { resourceId: ResourceId =>
    resourcesManager.delete(resourceId).flatMap {
      case Right(())               => Ok(Success(Some(s"Deleted [$resourceId]")))
      case Left(_: NoSuchResource) => NotFound(Error(s"No such resource was found by $resourceId"))
    }
  }

}
