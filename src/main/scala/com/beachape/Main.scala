package com.beachape

import java.util.concurrent.Executors

import cats.effect.IO
import fs2.{Stream, StreamApp}
import fs2.StreamApp.ExitCode

import scala.concurrent.ExecutionContext

/**
  * This is the main entry point to running our server.
  *
  * This end of the world is where we finally specify that the `Effect` that we are running our
  * code with is `IO`.
  */
object Main extends StreamApp[IO] {

  override def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, ExitCode] = {
    implicit val serverECtx: ExecutionContext = {
      val pool = Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors)
      ExecutionContext.fromExecutor(pool)
    }
    for {
      server   <- Stream.eval(new Wiring[IO].server)
      exitCode <- server.serve
    } yield exitCode
  }

}
