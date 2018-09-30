package com.beachape.infra.persistence.models

import com.beachape.ids.{DeploymentId, Ref}
import com.beachape.infra.persistence.enums.ResourceKind

final case class DeploymentEntity(id: DeploymentId, name: String, resources: Seq[ResourceEntity])

object DeploymentEntity {

  final case class Data(name: String) extends AnyVal

  final case class CreateWithResources(
      name: String,
      resources: Seq[CreateResource]
  )

  final case class CreateResource(
      ref: Ref,
      kind: ResourceKind,
      name: String,
      cpuBoost: Boolean,
      memLimit: Boolean
  )

}
