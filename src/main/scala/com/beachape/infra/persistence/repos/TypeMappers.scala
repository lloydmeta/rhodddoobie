package com.beachape.infra.persistence.repos
import com.beachape.ids.{DeploymentId, Ref, ResourceId}
import com.beachape.infra.persistence.enums._
import doobie.Meta

/**
  * Module that holds various complex-Scala-type-to-simple-SQL-mappable-type typeclass instances for Doobie.
  *
  * Imported where needed.
  */
object TypeMappers {

  implicit val deploymentIdMeta: Meta[DeploymentId] =
    Meta.LongMeta.xmap(DeploymentId.apply, _.value)

  implicit val resourceIdMeta: Meta[ResourceId] =
    Meta.LongMeta.xmap(ResourceId.apply, _.value)

  implicit val refMeta: Meta[Ref] =
    Meta.StringMeta.xmap(Ref.apply, _.value)

  implicit val resourceKindMeta: Meta[ResourceKind] =
    Meta.StringMeta.xmap(ResourceKind.withName, _.entryName)

}
