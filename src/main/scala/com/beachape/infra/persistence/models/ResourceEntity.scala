package com.beachape.infra.persistence.models

import com.beachape.ids.{DeploymentId, Ref, ResourceId}
import com.beachape.infra.persistence.enums.ResourceKind

final case class ResourceEntity(id: ResourceId,
                                deploymentId: DeploymentId,
                                kind: ResourceKind,
                                ref: Ref,
                                name: String,
                                cpuBoost: Boolean,
                                memLimit: Boolean)

object ResourceEntity {

  final case class Data(
      name: String,
      cpuBoost: Boolean,
      memLimit: Boolean
  )
}
