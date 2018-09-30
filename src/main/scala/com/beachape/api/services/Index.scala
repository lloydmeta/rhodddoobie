package com.beachape.api.services

import cats.effect.Effect
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Location

/**
  * This is the root router; sends a redirect to the WebJar-provided Swagger UI with the proper `url` query param in
  * place so that it auto-loads the Swagger spec.
  */
@SuppressWarnings(Array("org.wartremover.warts.Throw"))
class Index[F[_]: Effect](initSwaggerPath: Uri) extends Http4sDsl[F] {

  implicit val uriQueryParamEncode: QueryParamEncoder[Uri] {
    def encode(value: Uri): QueryParameterValue
  } = new QueryParamEncoder[Uri] {
    override def encode(value: Uri) =
      QueryParameterValue(value.toString)
  }

  val service: HttpService[F] = HttpService[F] {
    case GET -> Root =>
      TemporaryRedirect(
        Location(uri("/assets/swagger-ui/3.19.0/index.html")
          .withQueryParam("url", initSwaggerPath)))
  }

}
