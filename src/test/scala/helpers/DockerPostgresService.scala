package helpers

import java.sql.DriverManager

import com.beachape.config.JDBCAdapter.Psql
import com.beachape.config.DBConf
import com.whisk.docker._
import com.whisk.docker.impl.spotify.DockerKitSpotify
import com.whisk.docker.scalatest.DockerTestKit
import org.scalatest.Suite

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

import org.log4s._

/**
  * Mixing this service in will actually cause tests to spawn Docker containers before running
  */
trait DockerPostgresService extends DockerKitSpotify with DockerTestKit { this: Suite =>

  import scala.concurrent.duration._

  final val PostgresContainerPort: Int = 5432

  /**
    * Override this config to change the DB Container that gets instantiated.
    */
  val PostgresDBConfig: DBConf = DBConf(
    host = dockerExecutor.host,
    port = PortManager.allocate(),
    user = "nph",
    password = "suitup",
    name = "database",
    autoMigrate = true,
    jdbcAdapter = Psql
  )

  val postgresContainer: DockerContainer = {
    DockerContainer("postgres:9.6.5-alpine")
      .withPorts((PostgresContainerPort, Some(PostgresDBConfig.port)))
      .withEnv(s"POSTGRES_USER=${PostgresDBConfig.user}",
               s"POSTGRES_PASSWORD=${PostgresDBConfig.password}",
               s"POSTGRES_DB=${PostgresDBConfig.name}")
      .withReadyChecker(
        new PostgresReadyChecker(PostgresDBConfig)
          .looped(1000, 50.millis)
      )
  }

  abstract override def dockerContainers: List[DockerContainer] =
    postgresContainer :: super.dockerContainers

  override def afterAll(): Unit = {
    super.afterAll()
    PortManager.deallocate(PostgresDBConfig.port)
    ()
  }
}

class PostgresReadyChecker(dbConf: DBConf) extends DockerReadyChecker {

  private val logger = getLogger

  override def apply(container: DockerContainerState)(implicit docker: DockerCommandExecutor,
                                                      ec: ExecutionContext): Future[Boolean] =
    container
      .getPorts()
      .map { ports =>
        logger.info(s"ports $ports")
        Try {
          for {
            connection <- Option(
              DriverManager.getConnection(dbConf.jdbcUrl.toString, dbConf.user, dbConf.password))
            _ = connection.close()
          } yield true
        }.toOption.flatten.getOrElse(false)
      }
}
