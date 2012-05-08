import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

    val appName         = "resource-test"
    val appVersion      = "1.0-SNAPSHOT"

    val appDependencies = Seq(
      // Add your project dependencies here,
    )

    val mongoDbPluginProject = Project("mongodb-plugin", file("modules/mongodb-plugin"))

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
    ).dependsOn(mongoDbPluginProject)

}
