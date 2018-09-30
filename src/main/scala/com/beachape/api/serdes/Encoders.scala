package com.beachape.api.serdes
import cats.Applicative
import com.beachape.api.enums.ResourceKind
import com.beachape.ids.{DeploymentId, Ref, ResourceId}
import io.circe.Encoder
import org.http4s.EntityEncoder
import org.http4s.circe._

object Encoders extends JsonEncoders with Encoders

trait Encoders {

  /**
    * The manually-written implicits are so that we don't have a circular implicit resolutin on EntityEncoder..
    */
  implicit def entityEncoderFromJsonEncoder[F[_], A: Encoder](
      implicit entityEncoder: EntityEncoder[F, String],
      fApplicative: Applicative[F],
      aEncoder: Encoder[A]): EntityEncoder[F, A] =
    jsonEncoderOf[F, A](entityEncoder, fApplicative, aEncoder)

}

trait JsonEncoders {

  implicit val deploymentIdJsonEncoder: Encoder[DeploymentId] =
    Encoder.encodeLong.contramap(_.value)

  implicit val resourceIdJsonEncoder: Encoder[ResourceId] =
    Encoder.encodeLong.contramap(_.value)

  implicit val refJsonEncoder: Encoder[Ref] =
    Encoder.encodeString.contramap(_.value)

  implicit val resourceKindEncoder: Encoder[ResourceKind] =
    Encoder.encodeString.contramap(_.entryName)

}
