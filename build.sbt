import org.typelevel.Dependencies._

ThisBuild / scalaVersion := "2.12.8"
ThisBuild / organization := "com.dispalt"

val vAll = Versions(versions, libraries, scalacPlugins)
val gh = GitHubSettings(org = "dispalt", proj = "tagless-redux", publishOrg = "com.dispalt", license = apache)

lazy val root = (project in file("."))
  .settings(
    noPublishSettings,
  )
  .aggregate(macros, tests)

lazy val macros = (project in file("macros"))
  .settings(
    name := "tagless-redux-macros",
    libraryDependencies += "org.typelevel" %% "cats-tagless-core" % "0.2.0",
    macroSettings,
  )
  .settings(commonSettings ++ buildSettings ++ publishSettings)
  .settings(addLibs(vAll, "cats-core"))
  .settings(addCompilerPlugins(vAll, "kind-projector"))
  .settings(addTestLibs(vAll, "scalatest", "cats-free", "cats-effect"))

lazy val tests = (project in file("tests"))
  .settings(
    name := "tagless-redux-tests",
    noPublishSettings,
    macroSettings,
  )
  .settings(addCompilerPlugins(vAll, "kind-projector"))
  .dependsOn(macros % "compile->compile;test->test")

lazy val macroSettings: Seq[Def.Setting[_]] = Seq(
  resolvers += Resolver.sonatypeRepo("releases"),
  resolvers += Resolver.bintrayRepo("scalameta", "maven"),
  libraryDependencies +=
    compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
)

lazy val commonSettings = sharedCommonSettings ++ Seq(
  parallelExecution in Test := false,
  scalaVersion := vAll.vers("scalac_2.12"),
  crossScalaVersions := Seq(scalaVersion.value),
  developers := List(Developer("Dan Di Spaltro", "@dispalt", "dan.dispaltro@gmail.com", new java.net.URL("http://dispalt.com")))
) ++ scalacAllSettings ++ unidocCommonSettings ++
  addCompilerPlugins(vAll, "kind-projector")

lazy val buildSettings = sharedBuildSettings(gh, vAll)

lazy val publishSettings = sharedPublishSettings(gh) ++ sharedReleaseProcess