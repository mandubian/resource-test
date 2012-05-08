name := "Play 2.0 MongoDB Plugin"

organization := "play.modules.mongodb"

version := "0.1-SNAPSHOT"

resolvers ++= Seq(
  "Typesafe repository snapshots" at "http://repo.typesafe.com/typesafe/snapshots/",
  "Typesafe repository releases" at "http://repo.typesafe.com/typesafe/releases/",
  Resolver.url("Play", url("http://download.playframework.org/ivy-releases/"))(Resolver.ivyStylePatterns)
)

libraryDependencies ++= Seq(
  "com.mongodb.casbah" %% "casbah" % "2.1.5-1",
  "play" %% "play" % "2.1-SNAPSHOT",
  "play" %% "play-test" % "2.1-SNAPSHOT",
  "org.specs2" %% "specs2" % "1.7.1" % "test",
  "junit" % "junit" % "4.8" % "test"  
)