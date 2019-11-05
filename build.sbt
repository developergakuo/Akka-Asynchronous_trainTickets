name := "TrainService"

version := "0.1"

scalaVersion := "2.12.7"
val akkaVersion = "2.4.19"
val scalaMetaVersion = "4.1.0"

// the library is available in Bintray repository
resolvers += "dnvriend" at "http://dl.bintray.com/dnvriend/maven"

// akka 2.5.x
libraryDependencies += "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.15.1"
libraryDependencies += "com.typesafe.akka" %% "akka-persistence" % "2.5.15"
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.5.15"

libraryDependencies += "org.scalatest" % "scalatest_2.12" % "3.0.5"

libraryDependencies += "commons-io" % "commons-io" % "2.6"

libraryDependencies += "com.typesafe.akka"  %% "akka-testkit" % akkaVersion   % "test"