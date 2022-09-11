ThisBuild / tlBaseVersion := "0.0"

ThisBuild / organization := "com.armanbilge"
ThisBuild / organizationName := "Arman Bilge"
ThisBuild / developers += tlGitHubDev("armanbilge", "Arman Bilge")
ThisBuild / startYear := Some(2022)
ThisBuild / tlSonatypeUseLegacyHost := false

ThisBuild / crossScalaVersions := Seq("3.1.3", "2.12.16", "2.13.8")

ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))
ThisBuild / githubWorkflowOSes :=
  Seq("ubuntu-20.04", "ubuntu-22.04", "macos-11", "macos-12")
ThisBuild / githubWorkflowBuildMatrixExclusions ++= {
  for {
    scala <- crossScalaVersions.value.init
    os <- githubWorkflowOSes.value.tail
  } yield MatrixExclude(Map("scala" -> scala, "os" -> os))
}

ThisBuild / githubWorkflowBuild ++= Seq(
  WorkflowStep.Sbt(
    List("example/run"),
    name = Some("Run the example"),
    cond = Some("matrix.project == 'rootNative'")
  )
)

val catsEffectVersion = "3.3.14-1-5d11fe9"
val munitCEVersion = "2.0-5e03bfc"

lazy val root = tlCrossRootProject.aggregate(core, tests, example)

lazy val core = project
  .in(file("core"))
  .enablePlugins(ScalaNativePlugin)
  .settings(
    name := "epollcat",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-effect" % catsEffectVersion
    )
  )

lazy val tests = crossProject(JVMPlatform, NativePlatform)
  .in(file("tests"))
  .enablePlugins(NoPublishPlugin)
  .nativeConfigure(_.dependsOn(core))
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-effect" % catsEffectVersion,
      "org.typelevel" %%% "munit-cats-effect" % munitCEVersion % Test
    )
  )

lazy val example = project
  .in(file("example"))
  .enablePlugins(ScalaNativePlugin, NoPublishPlugin)
  .dependsOn(tests.native)
