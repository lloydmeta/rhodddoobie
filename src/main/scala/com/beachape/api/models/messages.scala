package com.beachape.api.models

final case class Error(message: String) /* extends AnyVal <Making this AnyVal causes Rho to bug out> */
final case class Success(message: Option[String]) /* extends AnyVal <Making this AnyVal causes Rho to bug out> */
