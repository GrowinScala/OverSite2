name := """oversite2"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

resolvers += Resolver.sonatypeRepo("snapshots")

scalaVersion := "2.12.7"

libraryDependencies ++= Seq(
  guice,
  "com.typesafe.slick" %% "slick-hikaricp" % "3.3.2",
  "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.0" % Test,
  "mysql" % "mysql-connector-java" % "8.0.15",
  "org.mockito" % "mockito-core" % "2.8.47" % "test"
)

