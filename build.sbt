name := """oversite2"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

resolvers += Resolver.sonatypeRepo("snapshots")

scalaVersion := "2.12.7"

libraryDependencies ++= Seq(
  guice,
  "com.typesafe.play" %% "play-slick" % "4.0.1",
  "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.0" % Test,
  "mysql" % "mysql-connector-java" % "8.0.15"
)

