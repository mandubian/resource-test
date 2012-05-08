// Comment to get more information during initialization
logLevel := Level.Warn

resolvers ++= Seq(
    Resolver.file("local snapshot", file("/Volumes/PVO/workspaces/workspace_zen/Play20/repository/local"))(Resolver.ivyStylePatterns),
    DefaultMavenRepository,
    Resolver.url("Play", url("http://download.playframework.org/ivy-releases/"))(Resolver.ivyStylePatterns),
    "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
)

// Use the Play sbt plugin for Play projects
addSbtPlugin("play" % "sbt-plugin" % "2.1-SNAPSHOT")


