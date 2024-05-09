import ReleaseTransformations._

val currentScalaVersion = "2.13.14"
val scalaVersions = Seq("2.12.17", currentScalaVersion)
val awsVersion = "2.25.46"
val pekkoVersion = "1.0.2"
val catsCore = "org.typelevel" %% "cats-core" % "2.10.0"

val checkEvictionsTask = taskKey[Unit]("Task that fails build if there are evictions")

lazy val depOverrides = Seq(
  "org.slf4j" % "slf4j-api" % "1.7.36",
  "org.slf4j" % "jcl-over-slf4j" % "1.7.36",
  "io.netty" % "netty-codec-http" % "4.1.108.Final",
  "io.netty" % "netty-handler" % "4.1.108.Final",
  "commons-codec" % "commons-codec" % "1.15",
  "com.typesafe" % "config" % "1.4.3",
  "software.amazon.awssdk" % "sqs" % awsVersion,
  catsCore
)

lazy val commonSettings = Seq(
  addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.3" cross CrossVersion.full),
  scalaVersion := currentScalaVersion,
  crossScalaVersions := scalaVersions,
  scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, minor)) if minor >= 13 => Seq("-Ymacro-annotations", "-language:higherKinds")
    case _ => Seq("-language:higherKinds")
  }),
  libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, minor)) if minor >= 13 => Nil
    case _ => Seq(
        compilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full),
        "org.scala-lang.modules" % "scala-java8-compat_2.12" % "1.0.2")
  }),
  dependencyOverrides ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, minor)) if minor >= 13 => Seq(
        "org.scala-lang.modules" %% "scala-collection-compat" % "2.2.0")
    case _ => Nil
  }),
  libraryDependencies ++= Seq(
    "org.slf4j" % "slf4j-api" % "1.7.30",
    "org.slf4j" % "jcl-over-slf4j" % "1.7.30" % Test,
    "ch.qos.logback" % "logback-classic" % "1.2.13" % Test,
    "com.vladsch.flexmark" % "flexmark-all" % "0.64.8" % Test,
    "org.scalatest" %% "scalatest" % "3.2.18" % Test),
  dependencyOverrides ++= depOverrides,
  Compile / console / scalacOptions ~=
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
  organization := "com.nike.fleam",
  organizationName := "Nike",
  organizationHomepage := Some(url("http://engineering.nike.com")),
  releaseCrossBuild := true,
  publishTo := sonatypePublishToBundle.value,
  sonatypeProfileName := "com.nike",
  scalacOptions ++= sharedScalacOptions ++ Seq("-Xfatal-warnings", "-Xlint", "-Xlint:-adapted-args"),
  Compile / console / scalacOptions ++= sharedScalacOptions,
  Compile / doc / scalacOptions ++= sharedScalacOptions,
  licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
  homepage := Some(url("https://github.com/Nike-Inc/Fleam")),
  startYear := Some(2020),
  scmInfo := Some(ScmInfo(
    url("https://github.com/Nike-Inc/Fleam"),
    "scm:git@github.com:Nike-Inc/Fleam.git"
  )),
  developers := List(
    Developer(
      id = "vendamere",
      name = "Peter Vendamere",
      email = "vendamere@gmail.com",
      url = url("https://github.com/vendamere")
    )
  ))

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommandAndRemaining("+publishSigned"),
  releaseStepCommand("sonatypeBundleRelease"),
  setNextVersion,
  commitNextVersion,
  pushChanges
)

val coverageSettings = Seq(
  coverageMinimumStmtTotal := 60,
  coverageFailOnMinimum := true,
  Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oDF"),
  Test / testOptions +=
    Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/test-reports-html"))


lazy val core = (project in file("./core"))
  .enablePlugins(spray.boilerplate.BoilerplatePlugin)
  .settings(Revolver.settings: _*)
  .settings(commonSettings)
  .settings(releaseSettings)
  .settings(coverageSettings)
  .settings(
    name := "fleam",
    description := "Disjunctive and monadic stream processing with cats and pekko-streams",
    libraryDependencies ++= Seq(
      "org.apache.pekko" %% "pekko-stream" % pekkoVersion,
      "org.apache.pekko" %% "pekko-actor" % pekkoVersion,
      "org.apache.pekko" %% "pekko-slf4j" % pekkoVersion,
      catsCore,
      "org.typelevel" %% "simulacrum" % "1.0.1",
      "org.typelevel" %% "discipline-core" % "1.7.0" % Test
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
    name := "fleam-aws-sqs",
    description := "Fleam SQS is a library of classes to aid in processing AWS SQS messages in a functional manner",
    libraryDependencies += "software.amazon.awssdk" % "sqs" % awsVersion exclude("commons-logging", "commons-logging"),
    libraryDependencies += "com.nike.fawcett" %% s"fawcett-sqs-v2" % "0.4.0")

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
    libraryDependencies += "software.amazon.awssdk" % "cloudwatch" % awsVersion exclude("commons-logging", "commons-logging"))

lazy val docs = (project in file("./mdoc"))
  .dependsOn(core, sqs, cloudwatch)
  .enablePlugins(MdocPlugin)
  .settings(commonSettings)
  .settings(
    mdocIn := file("./mdoc"),
    mdocOut := file("./docs"))
  .settings(
    libraryDependencies ++= Seq(
      "org.json4s" %% "json4s-jackson" % "4.0.7",
      "com.iheart" %% "ficus" % "1.5.2"
    ),
    publish := (()),
    publishLocal := (()),
    publishArtifact := false,
    publish / skip := true,
    dependencyOverrides ++= Seq(
      "com.fasterxml.jackson.core" % "jackson-core" % "2.6.7",
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.6.7.3",
      "com.typesafe" % "config" % "1.4.2",
      "net.java.dev.jna" % "jna" % "5.14.0",
      "org.jboss.logging" % "jboss-logging" % "3.4.0.Final",
      "org.jboss.threads" % "jboss-threads" % "3.1.0.Final",
      "org.jboss.xnio" % "xnio-api" % "3.7.7.Final",
      "org.jboss.xnio" % "xnio-nio" % "3.7.7.Final",
      "org.jsoup" % "jsoup" % "1.10.2",
      "org.slf4j" % "slf4j-api" % "1.8.0-beta4",
      "org.wildfly.common" % "wildfly-common" % "1.5.2.Final",
    ))

lazy val fleam = (project in file("."))
  .aggregate(core, sqs, cloudwatch, docs)
  .settings(releaseSettings)
  .settings(addCommandAlias("check", "; +clean; checkEvictionsTask; +scalastyle; coverage; +test; coverageReport; docs/mdoc"): _*)
  .settings(
    publishArtifact := false,
    scalaVersion := currentScalaVersion,
    crossScalaVersions := scalaVersions)
