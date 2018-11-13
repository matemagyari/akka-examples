name := "akka-examples"

organization := "org.home"

version := "1.0"

scalaVersion := "2.12.4"

libraryDependencies ++= {
  val slf4jVersion = "1.7.21"
  val akkaVersion = "2.5.12"
  val logbackVersion = "1.1.3"
  Seq(
    //Logging
    "org.slf4j" % "slf4j-api" % slf4jVersion,
    "org.slf4j" % "log4j-over-slf4j" % slf4jVersion,
    "ch.qos.logback" % "logback-classic" % logbackVersion % "runtime",
    "ch.qos.logback" % "logback-core" % logbackVersion % "runtime",
    //akka
    "com.typesafe" % "config" % "1.3.0",
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
    "org.scalatest" %% "scalatest" % "3.0.5" % "test"
  )
}
