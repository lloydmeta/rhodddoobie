package com.beachape

import java.util.concurrent.Executors

import cats.effect.IO
import com.beachape.config.{AppConf, DBConf}
import fs2.{Stream, StreamApp}
import org.http4s.server.blaze._
import org.http4s.server.staticcontent.{MemoryCache, WebjarService, webjarService}
import com.beachape.api.services.{Deployments, Index, Resources}
import com.beachape.app.services.{DeploymentsManager, ResourcesManager}
import com.beachape.infra.persistence.repos.{DeploymentsRepo, ResourcesRepo}
import com.beachape.infra.persistence.{HikariOps, Migration}
import doobie.hikari.HikariTransactor
import fs2.StreamApp.ExitCode
import org.http4s.HttpService
import org.http4s.rho.swagger.syntax.{io => ioSwagger}
import org.http4s.rho.swagger.syntax.io._
import org.http4s.dsl.io._
import org.http4s.rho.swagger.models.Info

import scala.concurrent.ExecutionContext

object Main extends StreamApp[IO] {

  override def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, ExitCode] = {
    implicit val serverECtx: ExecutionContext = {
      val pool = Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors)
      ExecutionContext.fromExecutor(pool)
    }
    for {
      server   <- Stream.eval(wiring.server)
      exitCode <- server.serve
    } yield exitCode
  }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  object wiring {

    def server: IO[BlazeBuilder[IO]] =
      for {
        conf <- appConf
        db   <- database(conf.db)
        swaggerUri      = uri("/swagger.json")
        resetEndpoints  = rhoServices(db)
        indexEndpoints  = new Index(swaggerUri).service
        webjarEndpoints = webjarService[IO](WebjarService.Config(cacheStrategy = MemoryCache[IO]))
      } yield
        BlazeBuilder[IO]
          .bindHttp(conf.server.httpPort, "0.0.0.0")
          .mountService(indexEndpoints, "/")
          .mountService(resetEndpoints, "/")
          // http://localhost/assets/swagger-ui/3.2.2/index.html
          .mountService(webjarEndpoints, "/assets")

    private def appConf: IO[AppConf] =
      for {
        appConfigEither <- IO(AppConf.load())
        appConfig = appConfigEither match {
          case Right(c) => c
          case Left(err) => {
            throw new IllegalStateException(
              s"Could not load AppConfig: ${err.toList.mkString("\n")}")
          }
        }
      } yield appConfig

    private def database(dbConf: DBConf): IO[HikariTransactor[IO]] =
      for {
        _  <- Migration.withConfig(dbConf)
        xa <- HikariOps.toTransactor(dbConf)
      } yield xa

    private def rhoServices(dbTransactor: HikariTransactor[IO]): HttpService[IO] = {
      // Repos
      val deploymentsRepo = DeploymentsRepo.doobie(dbTransactor)
      val resourcesRepo   = ResourcesRepo.doobie(dbTransactor)

      // App services
      val deploymentsManager = new DeploymentsManager(deploymentsRepo)
      val resourcesManager   = new ResourcesManager(resourcesRepo)

      // Rho-powered services
      val deploymentsRhoService = new Deployments(deploymentsManager, ioSwagger)
      val resourcesRhoService   = new Resources(resourcesManager, ioSwagger)
      // compose them together
      val combinedRhoServices = deploymentsRhoService.and(resourcesRhoService)
      // Rho-powered services to normal http4s services
      val rhoMiddleware = createRhoMiddleware(
        apiInfo = Info(title = "Rho-powered Deployments", version = "0.0.420"))
      combinedRhoServices.toService(rhoMiddleware)
    }
  }

}
