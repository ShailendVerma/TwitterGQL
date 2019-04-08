name := "TwitterGQL"

version := "0.1"

scalaVersion := "2.12.8"

test in assembly := {}

//Akk http
libraryDependencies += "com.typesafe.akka" %% "akka-http"   % "10.1.8"

//Akka streams
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.5.19"

//Sangria
libraryDependencies += "org.sangria-graphql" %% "sangria" % "1.4.2"

//Logging - with macros to generate log level checks
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"

//Logback - implementation
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"

//spray json
libraryDependencies += "io.spray" %%  "spray-json" % "1.3.5"

//Config
libraryDependencies += "com.typesafe" % "config" % "1.3.2"