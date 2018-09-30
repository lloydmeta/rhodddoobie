# Rho + DDD * doobie + docker scratchpad [![Build Status](https://travis-ci.org/lloydmeta/rhodddoobie.svg?branch=master)](https://travis-ci.org/lloydmeta/rhodddoobie)

My little sandbox for playing around with the FP + OOP + DDD combination, and in particular using Rho, doobie, docker,
testing, etc in a project. 

Goals:
* Finding that sweet spot between FP, OOP and *light* DDD
* Not-completely-trivial CRUD with one-to-many parent-child modeling + endpoints
* Docker 
  * For booting up app w/ db
  * For integration tests
  * Migrations tests
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

[Docker](https://www.docker.com/) is essential to having fun here, so go download it if you don't have it installed

### Deployments API Swagger

`sbt dockerComposeUp` and go to the root in your browser (e.g. [localhost](http://localhost)).

### Tests

* `sbt test` will run normal tests
* `sbt dockerComposeTest` will run integration tests
* `sbt dockerComposeUp` will start a docker-compose environment that includes the web-server and dependencies.

## Notes

### On Rho

* Tying route definition + doc generation together with route implementation is genius.  It means the spec is always up
  to date
    * The type-level mechanism (e.g. that huge HList for capturing status + response types) is amazing.  Multiple response 
      type according to status provided seamlessly
* Areas for improvement
    * Support for more areas of spec generation
       * On models themselves; completely lacking, save for `withCustomSerializer` usage (see [docs](https://github.com/http4s/rho/blob/master/Rho.md)),
         which is not optimal (far away from actual models).  Would be awesome if it could be extended to use Swagger API
         annotations, for example
       * Support more things like endpoint tags and summary (maybe these exist, not sure)

### On http4s

* Integration with Cats standard libs is *amazing*
* Comes out of the box with support for various middlewares (auth and there is also [tsec](https://jmcardon.github.io/tsec/),
  a crypto lib with an http4s integration module.
* Used in production at [a number of places](https://http4s.org/adopters/), including Verizon, Jack Henry, etc.  
* Various integrations exist, like
  * [kamon-http4s](https://github.com/kamon-io/kamon-http4s)
  * [typedapi](https://github.com/pheymann/typedapi)  

### On Chimney

* This is the real deal for DDD in Scala. Makes DDD so easy and painless.

## Feedback

Help me learn ! As questions, submit issues and PRs :)