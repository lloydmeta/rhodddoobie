package com.beachape

import cats.effect.IO
import com.beachape.api.models._
import io.circe.Json
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, DiagrammedAssertions, FunSpec}
import org.http4s.{Method, Request, Status, Uri}
import org.http4s.client.blaze._
import org.log4s._
import org.scalatest.OptionValues._
import cats.syntax.all._
import com.beachape.api.models.Deployment.ResourceInDeploymentData
import com.beachape.api.enums.ResourceKind
import com.beachape.ids.Ref
import org.http4s.client.UnexpectedStatus
import org.scalactic.TypeCheckedTripleEquals

import scala.util.control.NonFatal
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._
import io.scalaland.chimney.dsl._

class AppServerSpec
    extends FunSpec
    with TypeCheckedTripleEquals
    with DiagrammedAssertions
    with ScalaFutures
    with IntegrationPatience
    with BeforeAndAfterAll {

  private val logger = getLogger

  private val serverAccess = sys.props.get("rhodddoobie:8080").value
  private val httpClientIO = Http1Client[IO]()

  import io.circe.generic.auto._
  import com.beachape.api.serdes.Decoders._
  import com.beachape.api.serdes.Encoders._

  describe("Deployments endpoints") {

    it("should work initially be empty when there are no Deployments") {
      val result = httpClientIO.flatMap(_.expect[Seq[Deployment]](uri("deployments")))
      assert(result.unsafeRunSync().isEmpty)
    }

    it("should do CRUD properly") {
      val test = for {
        httpClient <- httpClientIO
        // Create a Deployment
        req = Request[IO](Method.POST, uri("deployments"))
          .withBody(
            Deployment.DeploymentDataWithResources(
              "name",
              Seq(
                ResourceInDeploymentData(ref = Ref("one"),
                                         name = "zwei",
                                         kind = ResourceKind.Elasticsearch,
                                         cpuBoost = false,
                                         memLimit = true),
                ResourceInDeploymentData(ref = Ref("drei"),
                                         name = "four",
                                         kind = ResourceKind.APM,
                                         cpuBoost = true,
                                         memLimit = true)
              )
            ))
        deployment  <- httpClient.expect[Deployment](req)
        deployments <- httpClient.expect[Seq[Deployment]](uri("deployments"))
        _ = assert(deployments.size === 1)
        _ = assert(deployments.contains(deployment))
        // Update the Deployment
        updatedDeployment = deployment.copy(name = "heyo")
        _ <- httpClient.expect[Deployment](
          Request[IO](Method.PUT, uri(s"deployments/${deployment.id.value}"))
            .withBody(updatedDeployment.transformInto[Deployment.DeploymentData])
        )
        retrieved <- httpClient.expect[Option[Deployment]](
          uri(s"deployments/${deployment.id.value}"))
        _ = assert(retrieved.value === updatedDeployment)

        // <-- testing Resource routes inside Deployments
        // Create a Resource
        req2 = Request[IO](
          Method.POST,
          uri(s"deployments/${deployment.id.value}/resources/${ResourceKind.APM}/ref"))
          .withBody(Resource.ResourceData(name = "display name", cpuBoost = false, memLimit = true))
        resource <- httpClient.expect[Resource](req2)

        resources <- httpClient.expect[Seq[Resource]](uri("resources"))
        _ = assert(resources.size === 3) // there are already 2
        _ = assert(resources.contains(resource))

        // Resource updating
        updatedResource = resource.copy(name = "heyo")
        _ <- httpClient.expect[Resource](
          Request[IO](Method.PUT,
                      uri(s"deployments/${deployment.id.value}/resources/${resource.kind}/ref"))
            .withBody(updatedResource.transformInto[Resource.ResourceData])
        )
        retrieved <- httpClient.expect[Option[Resource]](
          uri(s"deployments/${deployment.id.value}/resources/${resource.kind}/ref"))
        _ = assert(retrieved.value === updatedResource)

        // Resource Delete
        _ <- httpClient.expect[Json](
          Request[IO](Method.DELETE,
                      uri(s"deployments/${deployment.id.value}/resources/${resource.kind}/ref")))
        retrieveStatus <- httpClient.status(
          Request[IO](Method.GET,
                      uri(s"deployments/${deployment.id.value}/resources/${resource.kind}/ref")))
        _ = assert(retrieveStatus === Status.NotFound)
        // testing Resource routes inside Deployments -->

        // Delete the Deployment
        _ <- httpClient.expect[Json](
          Request[IO](Method.DELETE, uri(s"deployments/${deployment.id.value}")))
        retrieveStatus <- httpClient.status(
          Request[IO](Method.GET, uri(s"deployments/${deployment.id.value}")))
        _ = assert(retrieveStatus === Status.NotFound)
      } yield {
        ()
      }
      test.unsafeRunSync()
    }

  }

  describe("Resources endpoints") {

    it("should work initially be empty when there are no Resources") {
      val result = httpClientIO.flatMap(_.expect[Seq[Resource]](uri("resources")))
      assert(result.unsafeRunSync().isEmpty)
    }

    it("should do CRUD properly") {
      val test = for {
        httpClient <- httpClientIO
        // Create a DeploymentFirst
        req1 = Request[IO](Method.POST, uri("deployments"))
          .withBody(
            Deployment.DeploymentDataWithResources(
              "name",
              Seq(
                ResourceInDeploymentData(ref = Ref("one"),
                                         name = "zwei",
                                         kind = ResourceKind.Kibana,
                                         cpuBoost = false,
                                         memLimit = true),
                ResourceInDeploymentData(ref = Ref("drei"),
                                         name = "four",
                                         kind = ResourceKind.APM,
                                         cpuBoost = true,
                                         memLimit = true)
              )
            ))
        deployment <- httpClient.expect[Deployment](req1)

        // Create a Resource
        req2 = Request[IO](
          Method.POST,
          uri(s"deployments/${deployment.id.value}/resources/${ResourceKind.Elasticsearch}/ref"))
          .withBody(Resource.ResourceData(name = "display name", cpuBoost = false, memLimit = true))
        resource <- httpClient.expect[Resource](req2)

        resources <- httpClient.expect[Seq[Resource]](uri("resources"))
        _ = assert(resources.size === 3)
        _ = assert(resources.contains(resource))

        _ <- httpClient.expect[Json](
          Request[IO](Method.DELETE, uri(s"resources/${resource.id.value}")))

        resources <- httpClient.expect[Seq[Resource]](uri("resources"))
        _ = assert(resources.size === 2) // added 1 to existing two and deleted it

        retrieveStatus <- httpClient.status(
          Request[IO](Method.GET, uri(s"resources/${resource.id.value}")))
        _ = assert(retrieveStatus === Status.NotFound)
      } yield {
        ()
      }
      test.unsafeRunSync()
    }

  }

  override protected def beforeAll(): Unit = {
    def waitUntilUp(): IO[String] =
      httpClientIO.flatMap(_.expect[String](uri(""))).recoverWith {
        case _: UnexpectedStatus => IO.pure("Done")
        case NonFatal(err) =>
          logger.info(err)("Failed to hit the server")
          IO.sleep(1.second) *> waitUntilUp()
      }
    waitUntilUp().unsafeRunSync()
    ()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    httpClientIO.flatMap(_.shutdown).unsafeRunSync()
  }

  private def uri(path: String): Uri = Uri.unsafeFromString(s"http://$serverAccess/$path")

}
