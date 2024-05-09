ThisBuild / libraryDependencySchemes ++= Seq("org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always)

addSbtPlugin("io.spray" % "sbt-boilerplate" % "0.6.1")
addSbtPlugin("org.scalameta" % "sbt-mdoc" % "2.5.2")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.10.0")
addSbtPlugin("com.github.sbt" % "sbt-release" % "1.4.0")
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.0.12")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.2.1")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.10.0")
