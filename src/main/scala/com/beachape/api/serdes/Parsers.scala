package com.beachape.api.serdes

import cats.Monad
import com.beachape.api.enums.ResourceKind
import com.beachape.ids._
import org.http4s.rho.bits.{FailureResponse, ResultResponse, StringParser, SuccessResponse}

import scala.reflect.runtime.universe

trait Parsers[F[_]] {

  implicit val deploymentIdParser: StringParser[F, DeploymentId] =
    buildValueParser(StringParser.longParser)(DeploymentId)

  implicit val resourceIdParser: StringParser[F, ResourceId] =
    buildValueParser(StringParser.longParser)(ResourceId)

  implicit val refParser: StringParser[F, Ref] =
    buildValueParser(StringParser.strParser)(Ref)

  implicit val kindParser: StringParser[F, ResourceKind] = new StringParser[F, ResourceKind] {
    def parse(s: String)(implicit F: Monad[F]): ResultResponse[F, ResourceKind] =
      ResourceKind.withNameInsensitiveOption(s) match {
        case Some(r) => SuccessResponse(r)
        case None    => FailureResponse.pure(BadRequest.pure(s"Invalid Resource Kind: [$s]"))
      }
    def typeTag: Option[universe.TypeTag[ResourceKind]] =
      Some(implicitly[universe.TypeTag[ResourceKind]])
  }

  private def buildValueParser[A: universe.TypeTag, V](underlyingParser: StringParser[F, V])(
      build: V => A): StringParser[F, A] = new StringParser[F, A] {
    def parse(s: String)(implicit F: Monad[F]): ResultResponse[F, A] =
      underlyingParser.parse(s).map(build)
    val typeTag: Option[universe.TypeTag[A]] = Some(implicitly[universe.TypeTag[A]])
  }

}
