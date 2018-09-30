package com.beachape.config

import java.net.URI

import enumeratum._

import scala.collection.immutable

final case class DBConf(host: String,
                        port: Int,
                        name: String,
                        user: String,
                        password: String,
                        autoMigrate: Boolean,
                        jdbcAdapter: JDBCAdapter) {

  def driverClassName: String = jdbcAdapter.driverClassName
  def jdbcUrl: URI            = jdbcAdapter.jdbcUrl(this)

}

/**
  * Shim for working with JDBC
  */
sealed trait JDBCAdapter extends EnumEntry {
  def driverClassName: String
  def jdbcUrl(dbConf: DBConf): URI
}

case object JDBCAdapter extends Enum[JDBCAdapter] {

  val values: immutable.IndexedSeq[JDBCAdapter] = findValues

  case object Psql extends JDBCAdapter {
    val driverClassName: String = "org.postgresql.Driver"

    def jdbcUrl(dbConf: DBConf): URI = {
      URI.create(s"jdbc:postgresql://${dbConf.host}:${dbConf.port}/${dbConf.name}")
    }
  }

}
