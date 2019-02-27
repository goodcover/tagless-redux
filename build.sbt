import org.typelevel.Dependencies._

ThisBuild / scalaVersion := "2.12.8"
ThisBuild / organization := "com.dispalt"

val vAll = Versions(versions, libraries, scalacPlugins)

lazy val root = (project in file("."))
  .aggregate(macros, tests)

lazy val macros = (project in file("macros"))
  .settings(
    libraryDependencies += "org.typelevel" %% "cats-tagless-core" % "0.1.0",
    metaMacroSettings,
    scalacAllSettings,
    scalacOptions ++= Seq("-Ydelambdafy:inline"),
  )
  .settings(addLibs(vAll, "cats-core"))
  .settings(simulacrumSettings(vAll))
  .settings(addCompilerPlugins(vAll, "kind-projector"))
  .settings(addTestLibs(vAll, "scalatest", "cats-free", "cats-effect"))

lazy val tests = (project in file("tests"))
  .settings(
    metaMacroSettings,
    scalacAllSettings,
    scalacOptions ++= Seq("-Ydelambdafy:inline"),
  )
  .settings(addCompilerPlugins(vAll, "kind-projector"))
  .dependsOn(macros % "compile->compile;test->test")

lazy val metaMacroSettings: Seq[Def.Setting[_]] = Seq(
  resolvers += Resolver.sonatypeRepo("releases"),
  resolvers += Resolver.bintrayRepo("scalameta", "maven"),
  libraryDependencies +=
    compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
)
