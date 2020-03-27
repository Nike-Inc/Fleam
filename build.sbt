val currentScalaVersion = "2.13.1"
val scalaVersions = Seq("2.12.10", currentScalaVersion)
val awsVersion = "1.11.707"
val akkaVersion = "2.5.26"
val catsCore = "org.typelevel" %% "cats-core" % "2.1.0"

val checkEvictionsTask = taskKey[Unit]("Task that fails build if there are evictions")

lazy val depOverrides = Seq()

lazy val commonSettings = Seq(
  resolvers += Resolver.sonatypeRepo("releases"),
  addCompilerPlugin("org.typelevel" % "kind-projector" % "0.11.0" cross CrossVersion.full),
  scalaVersion := currentScalaVersion,
  crossScalaVersions := scalaVersions,
  scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, minor)) if minor >= 13 => Seq("-Ymacro-annotations", "-language:higherKinds")
    case _ => Seq("-language:higherKinds")
  }),
  libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, minor)) if minor >= 13 => Nil
    case _ => Seq(compilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full))
  }),
  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "org.slf4j" % "slf4j-api" % "1.7.25",
    "org.slf4j" % "jcl-over-slf4j" % "1.7.25",
    "com.vladsch.flexmark" % "flexmark-all" % "0.35.10" % Test,
    "org.scalatest" %% "scalatest" % "3.1.0" % Test),
  dependencyOverrides ++= depOverrides,
  scalacOptions in (Compile, console) ~=
    (_ filterNot Set("-Xfatal-warnings", "-Xlint", "-Ywarn-unused-import")),
  checkEvictionsTask := {
    if (evicted.value.reportedEvictions.nonEmpty) {
      throw new IllegalStateException(
        "There are some incompatible classpath evictions warnings." +
          " You can suppress them with dependencyOverrides setting.")
    }
  })

val sharedScalacOptions = Seq(
  "-deprecation",
  "-feature",
  "-unchecked")

val releaseSettings = Seq(
  resolvers += "nike" at "https://artifactory.nike.com/artifactory/all-repos",
  organization := "com.nike.fleam",
  releaseCrossBuild := true,
  scalacOptions ++= sharedScalacOptions ++ Seq("-Xfatal-warnings", "-Xlint", "-Xlint:-adapted-args"),
  scalacOptions in (Compile,console) ++= sharedScalacOptions,
  scalacOptions in (Compile,doc) ++= sharedScalacOptions,
  publishTo := {
    val repo = "https://artifactory.nike.com/artifactory/maven"
    if (isSnapshot.value)
      Some("snapshots" at s"$repo-snapshots")
    else
      Some("releases" at repo)
  },
  licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
  credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"))

val coverageSettings = Seq(
  coverageMinimum := 60,
  coverageFailOnMinimum := true,
  testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oDF"),
  testOptions in Test +=
    Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/test-reports-html"))


lazy val core = (project in file("./core"))
  .enablePlugins(spray.boilerplate.BoilerplatePlugin)
  .settings(Revolver.settings: _*)
  .settings(commonSettings)
  .settings(releaseSettings)
  .settings(coverageSettings)
  .settings(
    name := "fleam",
    description := "Disjunctive and monadic stream processing with cats and akka-streams",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      catsCore,
      "org.typelevel" %% "simulacrum" % "1.0.0",
      "org.typelevel" %% "discipline-core" % "1.0.0" % Test
    ),
    coverageExcludedPackages := "")

lazy val sqs = (project in file("./aws/sqs"))
  .dependsOn(core)
  .dependsOn(core % "test->test")
  .settings(Revolver.settings: _*)
  .settings(commonSettings)
  .settings(releaseSettings)
  .settings(coverageSettings)
  .settings(
    resolvers += Resolver.bintrayRepo("nike", "maven"),
    name := "fleam-aws-sqs",
    description := "Fleam SQS is a library of classes to aid in processing AWS SQS messages in a functional manner",
    libraryDependencies += "com.amazonaws" % "aws-java-sdk-sqs" % awsVersion exclude("commons-logging", "commons-logging"),
    libraryDependencies += "com.nike.fawcett" %% s"fawcett-sqs-v1" % "0.3.0")

lazy val cloudwatch = (project in file("./aws/cloudwatch"))
  .dependsOn(core)
  .dependsOn(core % "test->test")
  .settings(Revolver.settings: _*)
  .settings(commonSettings)
  .settings(releaseSettings)
  .settings(coverageSettings)
  .settings(
    name := "fleam-aws-cloudwatch",
    description := "Provides a class to create a flow which logs a count to Cloudwatch as part of the stream",
    libraryDependencies += "com.amazonaws" % "aws-java-sdk-cloudwatch" % awsVersion exclude("commons-logging", "commons-logging"))

lazy val docs = (project in file("./mdoc"))
  .dependsOn(core, sqs, cloudwatch)
  .enablePlugins(MdocPlugin)
  .settings(commonSettings)
  .settings(
    mdocIn := file("./mdoc"),
    mdocOut := file("./docs"))
  .settings(
    libraryDependencies ++= Seq(
      "org.scalameta" %% "mdoc" % "2.1.1",
      "org.json4s" %% "json4s-jackson" % "3.6.7",
      "com.iheart" %% "ficus" % "1.4.7"
    ),
    publish := (()),
    publishLocal := (()),
    publishArtifact := false,
    dependencyOverrides ++= Seq(
      "com.fasterxml.jackson.core" % "jackson-core" % "2.6.7",
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.6.7.3",
      "com.typesafe" % "config" % "1.3.4",
      "org.jboss.logging" % "jboss-logging" % "3.4.0.Final",
      "org.wildfly.common" % "wildfly-common" % "1.5.2.Final",
      "org.jboss.xnio" % "xnio-nio" % "3.7.7.Final",
      "org.jboss.xnio" % "xnio-api" % "3.7.7.Final",
      "org.jsoup" % "jsoup" % "1.10.2",
      "org.slf4j" % "slf4j-api" % "1.8.0-beta4",
    ))

lazy val fleam = (project in file("."))
  .aggregate(core, sqs, cloudwatch, docs)
  .settings(releaseSettings)
  .settings(addCommandAlias("check", "; +clean; checkEvictionsTask; +scalastyle; coverage; +test; coverageReport; docs/mdoc"): _*)
  .settings(
    publishArtifact := false,
    scalaVersion := currentScalaVersion,
    crossScalaVersions := scalaVersions)
