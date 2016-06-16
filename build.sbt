resolvers ++= Seq(
  Resolver.sonatypeRepo("releases")
)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.4.1",
  "com.chuusai"       %% "shapeless"  % "2.3.0"
)
