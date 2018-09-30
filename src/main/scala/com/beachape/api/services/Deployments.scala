package com.beachape.api.services

import cats.Monad
import cats.effect.Effect
import cats.syntax.all._
import com.beachape.api.models.{Deployment, Error, Resource, Success}
import org.http4s.EntityDecoder
import org.http4s.rho.RhoService
import org.http4s.rho.swagger.SwaggerSyntax
import io.circe.generic.auto._
import com.beachape.api.serdes.Encoders._
import com.beachape.api.serdes.Decoders._
import com.beachape.api.serdes.Parsers
import com.beachape.api.enums.ResourceKind
import com.beachape.app.services.DeploymentsManager
import com.beachape.app.services.DeploymentsManager._
import com.beachape.ids.{DeploymentId, Ref}

/**
  * This is our router/controller for handling Deployments.
  *
  * Note that we are extending `RhoService`, which gives us the ability to both define and implement
  * our routes **in addition to** writing the necessary metadata for generating OpenApi/Swagger
  * docs. Notice that *everything is data**
  *
  * As with many Sinatra/Scalatra style frameworks, the router/controller line is a bit blurred.
  */
@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements", "org.wartremover.warts.Any"))
class Deployments[F[+ _]: Effect: Monad](deploymentsManager: DeploymentsManager[F],
                                         swaggerSyntax: SwaggerSyntax[F])
    extends RhoService[F]
    with Parsers[F] {

  import swaggerSyntax._

  private val deployments = "deployments"
  private val resources   = "resources"

  private val deploymentIdVar =
    pathVar[DeploymentId](id = "deployment_id", description = "The id of a Deployment.")
  private val refVar =
    pathVar[Ref](id = "ref",
                 description = "User-specified reference id that is unique within a deployment.")
  private val kindVar =
    pathVar[ResourceKind](
      id = "resource_kind",
      description = s"The kind of Resource. One of: [${ResourceKind.values.mkString(", ")}].")

  "Get all the Deployments that have been persisted" **
    GET / deployments |>> deploymentsManager.list.flatMap(Ok(_))

  "Get a single Deployment by id" **
    GET / deployments / deploymentIdVar |>> { deploymentId: DeploymentId =>
    deploymentsManager.get(deploymentId).flatMap {
      case Some(deployment) => Ok(deployment)
      case None             => NotFound(Error(s"No Deployment with id [$deploymentId]."))
    }
  }

  "Create a single Deployment" **
    POST / deployments ^ EntityDecoder[F, Deployment.DeploymentDataWithResources] |>> {
    data: Deployment.DeploymentDataWithResources =>
      deploymentsManager.create(data).flatMap {
        case Right(deployment) => Created(deployment)
        case Left(NonUniqueResourcesDetected(nonUniques)) =>
          UnprocessableEntity(Error(
            s"Found Resources that are the same Kind and share the same Ref [${nonUniques.mkString(", ")}]"))
        case Left(NonUniqueResourcesInsertAttempted(attempted)) =>
          BadRequest(Error(
            s"Attempted to insert Resources that have the same Kind and Ref for the requested Deployment: [${attempted
              .mkString(", ")}]."))
      }
  }

  "Delete a Deployment" **
    DELETE / deployments / deploymentIdVar |>> { deploymentId: DeploymentId =>
    deploymentsManager.delete(deploymentId).flatMap {
      case Right(()) => Ok(Success(Some(s"Deleted [$deploymentId].")))
      case Left(_: NoSuchDeployment) =>
        NotFound(Error(s"No such Deployment was found by [$deploymentId]."))
    }
  }

  "Update a Deployment" **
    PUT / deployments / deploymentIdVar ^ EntityDecoder[F, Deployment.DeploymentData] |>> {
    (id: DeploymentId, data: Deployment.DeploymentData) =>
      deploymentsManager.update(id, data).flatMap {
        case Right(data) => Ok(data)
        case Left(_: NoSuchDeployment) =>
          NotFound(Error(s"No such deployment: [$id]."))
      }
  }

  "Create a Resource on a Deployment" **
    POST / deployments / deploymentIdVar / resources / kindVar / refVar ^
    EntityDecoder[F, Resource.ResourceData] |>> {
    (id: DeploymentId, kind: ResourceKind, ref: Ref, data: Resource.ResourceData) =>
      deploymentsManager.addResource(id, kind, ref, data).flatMap {
        case Right(resource) => Created(resource)
        case Left(_: NoSuchDeployment) =>
          NotFound(Error(s"No such deployment: [$id]"))
        case Left(e: AddResourceUniquenessViolation) =>
          BadRequest(Error(
            s"Could not insert Resource because [${e.kind}] [${e.ref}] already exists on Deployment [${e.deploymentId}]."))
      }
  }

  "Read a Resource on a Deployment" **
    GET / deployments / deploymentIdVar / resources / kindVar / refVar |>> {
    (id: DeploymentId, kind: ResourceKind, ref: Ref) =>
      deploymentsManager.getResource(id, kind, ref).flatMap {
        case Some(resource) => Ok(resource)
        case _              => NotFound(Error(s"No such Resource was found by [$id] [$kind] [$ref]."))
      }
  }

  "Update a Resource on a Deployment" **
    PUT / deployments / deploymentIdVar / resources / kindVar / refVar ^
    EntityDecoder[F, Resource.ResourceData] |>> {
    (id: DeploymentId, kind: ResourceKind, ref: Ref, data: Resource.ResourceData) =>
      deploymentsManager.updateResource(id, kind, ref, data).flatMap {
        case Right(resource) => Created(resource)
        case Left(e: NoSuchResource) =>
          NotFound(Error(s"No such Resource: [$e]"))
      }
  }

  "Delete a Resource on a Deployment" **
    DELETE / deployments / deploymentIdVar / resources / kindVar / refVar |>> {
    (id: DeploymentId, kind: ResourceKind, ref: Ref) =>
      deploymentsManager.deleteResource(id, kind, ref).flatMap {
        case Right(()) => Ok(Success(Some(s"Deleted Resource.")))
        case Left(noSuchResource) =>
          NotFound(Error(
            s"No such Resource was found by [${noSuchResource.deploymentId}] [${noSuchResource.kind}] [${noSuchResource.ref}]."))
      }
  }

}
