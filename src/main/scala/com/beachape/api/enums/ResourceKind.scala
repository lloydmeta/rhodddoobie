package com.beachape.api.enums

import enumeratum._

import scala.collection.immutable

sealed trait ResourceKind extends EnumEntry

object ResourceKind extends Enum[ResourceKind] {

  case object Elasticsearch extends ResourceKind
  case object Kibana        extends ResourceKind
  case object APM           extends ResourceKind

  val values: immutable.IndexedSeq[ResourceKind] = findValues
}
