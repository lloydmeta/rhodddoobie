package com.beachape.api.models
import com.beachape.api.enums.ResourceKind
import com.beachape.ids.{DeploymentId, Ref, ResourceId}

final case class Resource(id: ResourceId,
                          deploymentId: DeploymentId,
                          kind: ResourceKind,
                          ref: Ref,
                          name: String,
                          cpuBoost: Boolean,
                          memLimit: Boolean)

object Resource {

  // Rho doesn't know to namespace the types by their companion objects...
  final case class ResourceData(name: String, cpuBoost: Boolean, memLimit: Boolean)
}
