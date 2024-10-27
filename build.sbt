ThisBuild / tlBaseVersion := "0.1"

ThisBuild / organization := "com.armanbilge"
ThisBuild / organizationName := "Arman Bilge"
ThisBuild / developers += tlGitHubDev("armanbilge", "Arman Bilge")
ThisBuild / startYear := Some(2022)
ThisBuild / tlSonatypeUseLegacyHost := false

ThisBuild / crossScalaVersions := Seq("3.3.1", "2.12.18", "2.13.12")
ThisBuild / tlJdkRelease := None

ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))
ThisBuild / githubWorkflowOSes :=
  Seq("ubuntu-20.04", "ubuntu-22.04", "macos-11", "macos-12")
ThisBuild / githubWorkflowBuildMatrixExclusions ++= {
  for {
    scala <- List("3", "2.12")
    os <- githubWorkflowOSes.value.tail
  } yield MatrixExclude(Map("scala" -> scala, "os" -> os))
}

ThisBuild / githubWorkflowPublishPreamble +=
  WorkflowStep.Use(
    UseRef.Public("typelevel", "await-cirrus", "main"),
    name = Some("Wait for Cirrus CI")
  )

ThisBuild / githubWorkflowBuild ++= Seq(
  WorkflowStep.Sbt(
    List("example/run"),
    name = Some("Run the example"),
    cond = Some("matrix.project == 'rootNative'")
  )
)

val catsEffectVersion = "3.5.5"
val munitCEVersion = "2.0.0-M4"

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
    // https://github.com/scalameta/munit/blob/92710a507339d20368d251feadf66e4f9f4e1840/junit-interface/src/main/java/munit/internal/junitinterface/JUnitRunner.java#L75
    // note that the equivalent `--logger=sbt` did not work for some reason
    // using the sbt logger prevents tests from different suites being interleaved
    Test / testOptions += Tests.Argument("+l"),
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-effect" % catsEffectVersion,
      "org.typelevel" %%% "munit-cats-effect" % munitCEVersion % Test
    )
  )
  .jvmSettings(fork := true)

lazy val example = project
  .in(file("example"))
  .enablePlugins(ScalaNativePlugin, NoPublishPlugin)
  .dependsOn(tests.native)
