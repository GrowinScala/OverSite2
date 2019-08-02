name := """oversite2"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

resolvers += Resolver.sonatypeRepo("snapshots")

scalaVersion := "2.12.7"

libraryDependencies ++= Seq(
  guice,
  "com.typesafe.slick" %% "slick-hikaricp" % "3.3.2",
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test,
  "mysql" % "mysql-connector-java" % "8.0.15",
  "org.mockito" % "mockito-scala_2.12" % "1.5.12",
  "org.mockito" % "mockito-scala-scalatest_2.12" % "1.5.12",
  "com.h2database" % "h2" % "1.4.199" % Test,
  "com.github.t3hnar" %% "scala-bcrypt" % "4.1"
)

