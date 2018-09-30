# Rho + DDD + doobie + Docker scratchpad

[![Build Status](https://travis-ci.org/lloydmeta/rhodddoobie.svg?branch=master)](https://travis-ci.org/lloydmeta/rhodddoobie)

My little sandbox for playing around with the FP + OOP + DDD combination, in particular using Rho, doobie, Docker,
integration testing, etc in a project.  In the future, this might be spun out into a G8 template.

Goals:
* Finding that sweet spot between FP, OOP and *light* DDD
* Get feet wet with using Rho as a routing + API spec layer on http4s through building
  not-completely-trivial CRUD API endpoings with one-to-many parent-child modeling
* Docker
  * For booting up app w/ Psql db
  * For integration tests
  * Migrations tests
* Doobie for interfacing with Postgres
* H2 for simple repository tests

Explores:

* Using [http4s](http://http4s.org/) with [cats-effect](https://github.com/typelevel/cats-effect)
    * Using [rho](https://github.com/http4s/rho) for Swagger support
* [Chimney](https://scalalandio.github.io/chimney/) for assisting in DDD model transforms between layers
* Using [doobie](http://tpolecat.github.io/doobie/docs/01-Introduction.html) with cats-effect
* [Pureconfig](https://github.com/pureconfig/pureconfig) for loading configuration
* [Flyway](https://flywaydb.org/) for handling migrations
* [Docker testkit](https://github.com/whisklabs/docker-it-scala) for bringing up Docker containers on a per-test-suite basis
* [sbt-native-packager](http://sbt-native-packager.readthedocs.io/en/latest/) for bundling the app into a Docker image
* [sbt-docker-compose](https://github.com/Tapad/sbt-docker-compose) for bringing up a docker-compose environment based on the main project and a docker-compose.yml file
    * Can run the whole project from sbt via `sbt dockerComposeUp` (stopped via `dockerComposeStop`)
    * Also used for running Integration tests from a separate subproject
* [Swagger](https://swagger.io/) API spec w/ UI via Webjar

## Usage

[Docker](https://www.docker.com/) is essential to having fun here, so go download it if you don't have it installed.

### Browse the API Spec (Swagger)

`sbt dockerComposeUp` and go to the root in your browser (e.g. [localhost](http://localhost)). It will start a
docker-compose environment that includes the web-server and dependencies.

### Tests

* `sbt test` will run normal tests
* `sbt dockerComposeTest` will run integration tests

## Notes

### On Rho

* Tying route definition + doc generation together with route implementation is genius.  It means the spec is always up
  to date
    * The type-level mechanism (e.g. that huge HList for capturing status + response types) is amazing. A lot of things
      *just work*
         * Multiple branches in your code that respond with different **statuses** *and* **types** get reflected in
           the generated docs.
         * `AnyVal` wrappers are exposed in the documentation as their underlying type.
    * Composing routes + documentation is just by **value**
* At the "end of the world", you can combine all your Rho service into a single http4s service (currently by calling
  `.toService` and passing in a Rho middle ware). This causes the routes and specs to get compiled.
    * This means it's entirely possible to have Rho work along side APIs defined as vanilla http4s endpoints.
* Areas for improvement
  * Support for more areas of spec generation
    * On models themselves; completely lacking, save for `withCustomSerializer` usage (see [docs](https://github.com/http4s/rho/blob/master/Rho.md)),
      which is not optimal (far away from actual models).  Would be awesome if it:
      * It could be extended (or default to) using Swagger API annotations is they are there.
      * Allowed defining the case-ness (e.g. snake or camel)of outputted JSON schema
      annotations, for example
    * Support more things like endpoint tags and summary (maybe these exist, not sure)
  * Documentation is a bit sparse; there's a wiki and some examples ... but that's it.

### On http4s

* Integration with Cats standard libs is *amazing*
  * Not being tied to a specific **Effect** is very liberating (ie working with `F[_]` throughout is very clean, no need
    for `implicit eCtx: ExecutionContext` boilerplate everywhere)
* Comes out of the box with support for various middlewares (auth and there is also [tsec](https://jmcardon.github.io/tsec/),
  a crypto lib with an http4s integration module.
* Used in production at [a number of places](https://http4s.org/adopters/), including Verizon, Jack Henry, etc.
* Various integrations exist, like
  * [kamon-http4s](https://github.com/kamon-io/kamon-http4s)
  * [typedapi](https://github.com/pheymann/typedapi)
  * Various JSON libs like circe, json4s (powered by Jackson or Native), Play-JSON, etc.
* http4s itself supports running on various servers other than Blaze (default), including [as a Servlet](https://github.com/http4s/http4s/blob/934a69a63cd487db0d1ddf39d9e4168d9c7f6e9d/servlet/src/main/scala/org/http4s/servlet/Http4sServlet.scala#L216-L228), enabling usage in:
  * [Jetty](https://github.com/http4s/http4s/blob/934a69a63cd487db0d1ddf39d9e4168d9c7f6e9d/examples/jetty/src/main/scala/com/example/http4s/jetty/JettyExample.scala#L16-L28)
  * [Tomcat](https://github.com/http4s/http4s/blob/934a69a63cd487db0d1ddf39d9e4168d9c7f6e9d/examples/tomcat/src/main/scala/com/example/http4s/tomcat/TomcatExample.scala#L15-L28)
  
  This is interesting because it makes migration easy (mount old Servlets with new ones.)
  
### On Chimney

* This is the real deal for DDD in Scala. Makes DDD so easy and painless.

### On Doobie

* Integrates well with the rest of the system due to Cats as lingua franca
* What-you-write-is-what-you-get SQL usage
* JDBC-based so it still blocks a thread somewhere; there is [an issue](https://github.com/tpolecat/doobie/issues/581) for
  a way of emulating Slick3-style asynchronicity
* Safe: type-checked of course, but there are also query soundness checks (see `QuerySoundnessSpec`) to check your queries
  against a real DB.

### Docker usage

* Using docker through the sbt plugin system smooths out integration testing and local manual testing.  A boon for lowering
  the barrier of entry for devs getting started.

## Feedback

Help me learn ! Ask questions, submit issues and PRs :)