package com.beachape.app.services
import cats.data.{EitherT, OptionT}
import cats.effect.Effect
import cats.syntax.all._
import com.beachape.api.models.{Deployment, Resource}
import com.beachape.ids.{DeploymentId, Ref}
import com.beachape.infra.persistence.repos.DeploymentsRepo
import com.beachape.app.services.DeploymentsManager._
import com.beachape.api.enums.{ResourceKind => ApiResourceKind}
import com.beachape.infra.persistence.enums.ResourceKind
import com.beachape.infra.persistence.models.{DeploymentEntity, ResourceEntity}
import io.scalaland.chimney.dsl._

/**
  * Thin layer that:
  *
  *   1. Transforms between layer models
  *   2. Additional logic to run before hitting the repo (infra layer)
  *   3. Add / translate / collapse / massage errors from the layer underneath
  */
class DeploymentsManager[F[+ _]: Effect](deploymentsRepo: DeploymentsRepo[F]) {

  def create(deployment: Deployment.DeploymentDataWithResources)
    : F[Either[CreateDeploymentError, Deployment]] = {
    for {
      d <- EitherT.fromEither(ensureUniqueResources(deployment))
      r <- EitherT(deploymentsRepo.insert(d.transformInto[DeploymentEntity.CreateWithResources]))
        .bimap(
          // Force ourselves to handle other error kinds if our repo layer is refactored to return others
          { _: DeploymentsRepo.ResourceKindRefViolation =>
            /*
             * This shouldn't really happen because we do a uniqueness check
             * before trying to do an insert to the repository, but just serves
             * as an example of how to handle such a case (e.g. repo layer design
             * might change independently)
             *
             * Alternatively, we could choose to just do a EitherT.raise here at the app layer
             * for these cases that we know _should_ not happen.
             */
            NonUniqueResourcesInsertAttempted(deployment.resources): CreateDeploymentError
          },
          _.transformInto[Deployment]
        )
    } yield r
  }.value

  def list: F[Seq[Deployment]] = deploymentsRepo.list.map(_.transformInto[Seq[Deployment]])

  def get(deploymentId: DeploymentId): F[Option[Deployment]] =
    OptionT(deploymentsRepo.get(deploymentId)).map(_.transformInto[Deployment]).value

  def update(deploymentId: DeploymentId,
             data: Deployment.DeploymentData): F[Either[NoSuchDeployment, Deployment]] =
    EitherT(deploymentsRepo.update(deploymentId, data.transformInto[DeploymentEntity.Data]))
      .bimap(
        e => NoSuchDeployment(e.deploymentId),
        _.transformInto[Deployment]
      )
      .value

  def delete(deploymentId: DeploymentId): F[Either[NoSuchDeployment, Unit]] =
    deploymentsRepo.delete(deploymentId).map {
      case 1 => Right(())
      case _ => Left(NoSuchDeployment(deploymentId))
    }

  def addResource(deploymentId: DeploymentId,
                  kind: ApiResourceKind,
                  ref: Ref,
                  data: Resource.ResourceData): F[Either[AddResourceError, Resource]] = {
    def mkUniquenessErr: AddResourceError = AddResourceUniquenessViolation(deploymentId, kind, ref)
    for {
      existing <- getResource(deploymentId, kind, ref)
      r <- if (existing.isEmpty)
        /*
         * Here we lean on the repo telling us if the Deployment does not exist. We know
         * it has this functionality because the Left type of DeploymentsRepo#insertResource
         * is `InsertResourceError`, which has `NoSuchDeploymentError` as one of its members.
         *
         * We are then forced to handle it properly by the Scala compiler in `.bimap`'s error
         * case. Missing it will give us a compiler error.
         */
        EitherT(
          deploymentsRepo.insertResource(deploymentId,
                                         kind.transformInto[ResourceKind],
                                         ref,
                                         data.transformInto[ResourceEntity.Data]))
          .bimap(
            {
              case _: DeploymentsRepo.NoSuchDeploymentError =>
                NoSuchDeployment(deploymentId): AddResourceError
              case _: DeploymentsRepo.ResourceKindRefViolation =>
                mkUniquenessErr
            },
            _.transformInto[Resource]
          )
          .value
      else
        Left(mkUniquenessErr).pure[F]
    } yield r
  }

  def getResource(id: DeploymentId, kind: ApiResourceKind, ref: Ref): F[Option[Resource]] =
    OptionT(deploymentsRepo.getResource(id, kind.transformInto[ResourceKind], ref))
      .map(_.transformInto[Resource])
      .value

  def updateResource(id: DeploymentId,
                     kind: ApiResourceKind,
                     ref: Ref,
                     data: Resource.ResourceData): F[Either[NoSuchResource, Resource]] =
    EitherT(
      deploymentsRepo.updateResource(id,
                                     kind.transformInto[ResourceKind],
                                     ref,
                                     data.transformInto[ResourceEntity.Data]))
      .bimap(
        nsr => NoSuchResource(nsr.deploymentId, nsr.kind.transformInto[ApiResourceKind], nsr.ref),
        _.transformInto[Resource]
      )
      .value

  def deleteResource(id: DeploymentId,
                     kind: ApiResourceKind,
                     ref: Ref): F[Either[NoSuchResource, Unit]] =
    deploymentsRepo.deleteResource(id, kind.transformInto[ResourceKind], ref).map {
      case 1 => Right(())
      case _ => Left(NoSuchResource(id, kind, ref))
    }

  private def ensureUniqueResources(deployment: Deployment.DeploymentDataWithResources)
    : Either[CreateDeploymentError, Deployment.DeploymentDataWithResources] = {
    val kindAndRefs = deployment.resources.map(r => (r.kind, r.ref))
    val repeats     = kindAndRefs.diff(kindAndRefs.distinct)
    if (repeats.nonEmpty)
      Left(NonUniqueResourcesDetected(repeats))
    else
      Right(deployment)
  }

}

object DeploymentsManager {

  sealed trait Error
  sealed trait AddResourceError extends Error

  final case class NoSuchDeployment(deploymentId: DeploymentId) extends AddResourceError

  final case class NoSuchResource(deploymentId: DeploymentId, kind: ApiResourceKind, ref: Ref)
      extends Error

  sealed trait CreateDeploymentError extends Error

  final case class NonUniqueResourcesInsertAttempted(
      attempted: Seq[Deployment.ResourceInDeploymentData])
      extends CreateDeploymentError

  final case class NonUniqueResourcesDetected(repeats: Seq[(ApiResourceKind, Ref)])
      extends CreateDeploymentError

  final case class AddResourceUniquenessViolation(deploymentId: DeploymentId,
                                                  kind: ApiResourceKind,
                                                  ref: Ref)
      extends AddResourceError

}
