package com.beachape
import cats.implicits._
import cats.effect.Effect
import com.beachape.api.services.{Deployments, Index, Resources}
import com.beachape.app.services.{DeploymentsManager, ResourcesManager}
import com.beachape.config.{AppConf, DBConf}
import com.beachape.infra.persistence.{HikariOps, Migration}
import com.beachape.infra.persistence.repos.{DeploymentsRepo, ResourcesRepo}
import doobie.hikari.HikariTransactor
import org.http4s.HttpService
import org.http4s.dsl.io.uri
import org.http4s.rho.swagger.SwaggerSupport
import org.http4s.rho.swagger.models.Info
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.staticcontent.{MemoryCache, WebjarService, webjarService}

/**
  *  Our wiring module for our App, where do our value and type-level injection.
  */
@SuppressWarnings(
  Array("org.wartremover.warts.Throw",
        "org.wartremover.warts.Any",
        "org.wartremover.warts.NonUnitStatements"))
class Wiring[F[+ _]: Effect] {

  /**
    * Returns a standard http4s web server inside an F after wiring all the parts together
    */
  def server: F[BlazeBuilder[F]] =
    for {
      conf <- appConf
      db   <- database(conf.db)
      swaggerUri      = uri("/swagger.json")
      restEndpoints   = rhoServices(db)
      indexEndpoints  = new Index(swaggerUri).service
      webjarEndpoints = webjarService(WebjarService.Config(cacheStrategy = MemoryCache[F]))
    } yield
      BlazeBuilder[F]
        .bindHttp(conf.server.httpPort, "0.0.0.0")
        .mountService(indexEndpoints, "/")
        .mountService(restEndpoints, "/")
        .mountService(webjarEndpoints, "/assets")

  private def appConf: F[AppConf] =
    for {
      appConfigEither <- AppConf.load().pure[F]
      appConfig <- appConfigEither match {
        case Right(c) => c.pure[F]
        case Left(err) =>
          Effect[F].raiseError[AppConf](
            new IllegalStateException(s"Could not load AppConfig: ${err.toList.mkString("\n")}")
          )
      }
    } yield appConfig

  private def database(dbConf: DBConf): F[HikariTransactor[F]] =
    for {
      _  <- Migration.withConfig(dbConf)
      xa <- HikariOps.toTransactor(dbConf)
    } yield xa

  private def rhoServices(dbTransactor: HikariTransactor[F]): HttpService[F] = {
    // Repos
    val deploymentsRepo = DeploymentsRepo.doobie(dbTransactor)
    val resourcesRepo   = ResourcesRepo.doobie(dbTransactor)

    // App services
    val deploymentsManager = new DeploymentsManager(deploymentsRepo)
    val resourcesManager   = new ResourcesManager(resourcesRepo)

    val swaggerSyntax = SwaggerSupport[F]

    // Rho-powered services
    val deploymentsRhoService = new Deployments(deploymentsManager, swaggerSyntax)
    val resourcesRhoService   = new Resources(resourcesManager, swaggerSyntax)
    // Compose them together
    val combinedRhoServices = deploymentsRhoService.and(resourcesRhoService)
    // Rho-powered services to normal http4s services
    val rhoMiddleware = swaggerSyntax.createRhoMiddleware(
      apiInfo = Info(title = "Rho-powered Deployments", version = "0.0.420"))
    combinedRhoServices.toService(rhoMiddleware)
  }
}
