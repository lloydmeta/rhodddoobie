package helpers

import java.net.URI

import cats.effect.IO
import com.beachape.config.DBConf
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import com.zaxxer.hikari.util.UtilityElf.DefaultThreadFactory
import doobie.hikari.HikariTransactor
import org.flywaydb.core.Flyway
import org.scalatest.{BeforeAndAfterAll, Suite}

/**
  * Exposess a fully-migrated H2 database and an H2 hikari Transactor
  */
trait H2DatabaseService extends BeforeAndAfterAll { this: Suite =>

  private val num = PortManager.allocate()

  /**
   Override this to change certain things
    */
  val H2DBConfig: DBConf = DBConf(
    host = "irrelevant",
    port = 1234,
    user = "nph",
    password = "suitup",
    name = s"database-$num",
    autoMigrate = true,
    jdbcAdapter = null // Irrelevant
  )

  final val H2DriverName: String = "org.h2.Driver"

  private def toH2JDBCUrl(dbConf: DBConf): URI =
    URI.create(
      s"jdbc:h2:mem:${dbConf.name};MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE")

  private val hikariConfig: HikariConfig = {
    val jConfig = new HikariConfig()
    jConfig.setJdbcUrl(toH2JDBCUrl(H2DBConfig).toString)
    jConfig.setUsername(H2DBConfig.user)
    jConfig.setPassword(H2DBConfig.password)
    jConfig.setDriverClassName(H2DriverName)
    jConfig.setThreadFactory(new DefaultThreadFactory("HikariThread", false))
    jConfig
  }

  private final lazy val dataSource = new HikariDataSource(hikariConfig)
  final lazy val H2Transactor: HikariTransactor[IO] =
    HikariTransactor[IO](dataSource)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val dataSource = new HikariDataSource(hikariConfig)
    val flyway     = new Flyway()
    flyway.setDataSource(dataSource)
    flyway.migrate()
    dataSource.close()
    ()
  }

  override protected def afterAll(): Unit = {
    dataSource.close()
    PortManager.deallocate(num)
    super.afterAll()
  }
}
