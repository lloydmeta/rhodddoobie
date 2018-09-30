import java.io.File

name := """rhodddoobie"""

version := "1.0-SNAPSHOT"

organization := "com.beachape"

scalaVersion := "2.12.6"

cancelable in Global := true

val http4sVersion        = "0.18.18"
val doobieVersion        = "0.5.3"
val circeVersion         = "0.10.0"
val dockerTestkitVersion = "0.9.8"
val pureConfigVersion    = "0.9.2"

enablePlugins(AshScriptPlugin, DockerComposePlugin)

dockerBaseImage := "openjdk:8-jre-alpine"

dockerImageCreationTask := (publishLocal in Docker).value

// sbt-docker-compose settings
variablesForSubstitution in IntegrationTest := Map(
  "POSTGRES_DB" -> "database_test"
)

Defaults.itSettings

lazy val root = project.in(file(".")).configs(IntegrationTest)

//To use 'dockerComposeTest' to run tests in the 'IntegrationTest' scope instead of the default 'Test' scope:
// 1) Package the tests that exist in the IntegrationTest scope
testCasesPackageTask := (sbt.Keys.packageBin in IntegrationTest).value
// 2) Specify the path to the IntegrationTest jar produced in Step 1
testCasesJar := artifactPath.in(IntegrationTest, packageBin).value.getAbsolutePath
// 3) Include any IntegrationTest scoped resources on the classpath if they are used in the tests
testDependenciesClasspath := {
  val fullClasspathCompile   = (fullClasspath in Compile).value
  val classpathTestManaged   = (managedClasspath in IntegrationTest).value
  val classpathTestUnmanaged = (unmanagedClasspath in IntegrationTest).value
  val testResources          = (resources in IntegrationTest).value
  (fullClasspathCompile.files ++ classpathTestManaged.files ++ classpathTestUnmanaged.files ++ testResources)
    .map(_.getAbsoluteFile)
    .mkString(File.pathSeparator)
}

libraryDependencies ++= Seq(
  // Configuration
  "com.github.pureconfig" %% "pureconfig"            % pureConfigVersion,
  "com.github.pureconfig" %% "pureconfig-enumeratum" % pureConfigVersion,
  // Http
  "org.http4s" %% "rho-swagger"         % "0.18.0",
  "org.http4s" %% "http4s-circe"        % http4sVersion,
  "org.http4s" %% "http4s-dsl"          % http4sVersion,
  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  // DB
  "org.tpolecat"   %% "doobie-core"     % doobieVersion,
  "org.tpolecat"   %% "doobie-hikari"   % doobieVersion,
  "org.tpolecat"   %% "doobie-postgres" % doobieVersion,
  "org.postgresql" % "postgresql"       % "42.1.4",
  "org.flywaydb"   % "flyway-core"      % "4.2.0",
  // JSON
  "io.circe" %% "circe-core"    % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser"  % circeVersion,
  // Logging
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  // Swagger
  "org.webjars" % "swagger-ui" % "3.19.0",
  /// DDD support
  "io.scalaland" %% "chimney" % "0.2.1",
  // Test
  "com.h2database" % "h2"                   % "1.4.196"     % Test,
  "org.tpolecat"   %% "doobie-scalatest"    % doobieVersion % Test,
  "org.scalatest"  %% "scalatest"           % "3.2.0-SNAP9" % "test,it",
  "org.http4s"     %% "http4s-blaze-client" % http4sVersion % IntegrationTest,
  // Test-docker stuff
  "com.whisk" %% "docker-testkit-scalatest"    % dockerTestkitVersion % Test,
  "com.whisk" %% "docker-testkit-impl-spotify" % dockerTestkitVersion % Test
)

scalacOptions ++= Seq(
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-encoding",
  "utf-8", // Specify character encoding used by source files.
  "-explaintypes", // Explain type errors in more detail.
  "-feature", // Emit warning and location for usages of features that should be imported explicitly.
  "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
  "-language:experimental.macros", // Allow macro definition (besides implementation and application)
  "-language:higherKinds", // Allow higher-kinded types
  "-language:implicitConversions", // Allow definition of implicit functions called views
  "-unchecked", // Enable additional warnings where generated code depends on assumptions.
  "-Xcheckinit", // Wrap field accessors to throw an exception on uninitialized access.
  "-Xfatal-warnings", // Fail the compilation if there are any warnings.
  "-Xfuture", // Turn on future language features.
  "-Xlint:adapted-args", // Warn if an argument list is modified to match the receiver.
  "-Xlint:by-name-right-associative", // By-name parameter of right associative operator.
  "-Xlint:constant", // Evaluation of a constant arithmetic expression results in an error.
  "-Xlint:delayedinit-select", // Selecting member of DelayedInit.
  "-Xlint:doc-detached", // A Scaladoc comment appears to be detached from its element.
  "-Xlint:inaccessible", // Warn about inaccessible types in method signatures.
  "-Xlint:infer-any", // Warn when a type argument is inferred to be `Any`.
  "-Xlint:missing-interpolator", // A string literal appears to be missing an interpolator id.
  "-Xlint:nullary-override", // Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Xlint:nullary-unit", // Warn when nullary methods return Unit.
  "-Xlint:option-implicit", // Option.apply used implicit view.
  "-Xlint:package-object-classes", // Class or object defined in package object.
  "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
  "-Xlint:private-shadow", // A private field (or class parameter) shadows a superclass field.
  "-Xlint:stars-align", // Pattern sequence wildcard must align with sequence component.
  "-Xlint:type-parameter-shadow", // A local type parameter shadows a type already in scope.
  "-Xlint:unsound-match", // Pattern match may not be typesafe.
  "-Yno-adapted-args",    // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
//  "-Ypartial-unification", // Enable partial unification in type constructor inference
  "-Ywarn-dead-code", // Warn when dead code is identified.
  "-Ywarn-extra-implicit", // Warn when more than one implicit parameter section is defined.
  "-Ywarn-inaccessible", // Warn about inaccessible types in method signatures.
  "-Ywarn-infer-any", // Warn when a type argument is inferred to be `Any`.
  "-Ywarn-nullary-override", // Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Ywarn-nullary-unit", // Warn when nullary methods return Unit.
  "-Ywarn-numeric-widen", // Warn when numerics are widened.
  "-Ywarn-macros:after", // Warn unused after macro expansion
  "-Ywarn-unused:implicits", // Warn if an implicit parameter is unused.
  "-Ywarn-unused:imports", // Warn if an import selector is not referenced.
  "-Ywarn-unused:locals", // Warn if a local definition is unused.
  "-Ywarn-unused:params", // Warn if a value parameter is unused.
  "-Ywarn-unused:patvars", // Warn if a variable bound in a pattern is unused.
  "-Ywarn-unused:privates", // Warn if a private member is unused.
  "-Ywarn-value-discard" // Warn when non-Unit expression results are unused.
)

// Doctest setup
doctestTestFramework := DoctestTestFramework.ScalaTest

wartremoverErrors in (Compile, compile) ++= Warts.unsafe

scalafmtOnCompile := true

scalacOptions in (Compile, console) ~= (_.filterNot(
  Set(
    "-Ywarn-unused:imports",
    "-Xfatal-warnings"
  )))

scalacOptions in (Test, console) ~= (_.filterNot(
  Set(
    "-Ywarn-unused:imports",
    "-Xfatal-warnings"
  )))

scalacOptions in (Test, compile) ~= (_.filterNot(
  Set(
    "-Ywarn-unused:imports",
    "-Ywarn-unused:params",
    "-Xfatal-warnings"
  )))
