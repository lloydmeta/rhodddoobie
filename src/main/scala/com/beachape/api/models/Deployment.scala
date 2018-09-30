package com.beachape.api.models
import com.beachape.api.enums.ResourceKind
import com.beachape.ids.{DeploymentId, Ref}

final case class Deployment(id: DeploymentId, name: String, resources: Seq[Resource])

object Deployment {

  // Rho doesn't know to namespace the types by their companion objects...
  final case class DeploymentData(
      name: String
  )

  final case class DeploymentDataWithResources(
      name: String,
      resources: Seq[ResourceInDeploymentData]
  )

  final case class ResourceInDeploymentData(
      ref: Ref,
      kind: ResourceKind,
      name: String,
      cpuBoost: Boolean,
      memLimit: Boolean
  )
}
