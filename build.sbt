import org.typelevel.Dependencies._

ThisBuild / scalaVersion := "2.12.8"
ThisBuild / organization := "com.dispalt"

val vAll = Versions(versions, libraries, scalacPlugins)
val gh   = GitHubSettings(org = "dispalt", proj = "tagless-redux", publishOrg = "com.dispalt", license = apache)

val taglessV = "0.5"
val akkaV    = "2.5.25"
val chillV   = "0.9.3"

ideaExternalPlugins in ThisBuild := Seq.empty
ideaPluginName in ThisBuild := "tagless-redux-ijext"
ideaBuild in ThisBuild := "183.4886.37"

lazy val root = (project in file("."))
  .settings(noPublishSettings)
  .settings(commonSettings ++ buildSettings ++ publishSettings)
  .aggregate(macros, tests, `encoder-macros`, `encoder-kryo`, `intellij-ijext`, `encoder-akka`)

lazy val macros = (project in file("macros"))
  .settings(
    name := "tagless-redux-macros",
    libraryDependencies += "org.typelevel" %% "cats-tagless-macros" % taglessV,
      macroSettings,
    resourceGenerators in Compile += Def.task {
      val rootFolder = (resourceManaged in Compile).value / "META-INF"
      rootFolder.mkdirs()

      IO.write(rootFolder / "intellij-compat.json", s"""{
         |  "artifact": "com.dispalt % tagless-redux-ijext_2.12 % ${version.value}"
         |}""".stripMargin)

      Seq(rootFolder / "intellij-compat.json")
    },
  )
  .settings(commonSettings ++ buildSettings ++ publishSettings)
  .settings(addLibs(vAll, "cats-core"))
  .settings(addCompilerPlugins(vAll, "kind-projector"))
  .settings(addTestLibs(vAll, "scalatest", "cats-free", "cats-effect"))

lazy val tests = (project in file("tests"))
  .settings(commonSettings ++ buildSettings ++ publishSettings)
  .settings(name := "tagless-redux-tests", noPublishSettings, macroSettings)
  .settings(addCompilerPlugins(vAll, "kind-projector"))
  .dependsOn(macros % "compile->compile;test->test")

lazy val `encoder-macros` = (project in file("encoder-macros"))
  .settings(
    name := "tagless-redux-encoder-macros",
    libraryDependencies ++= Seq("org.typelevel" %% "cats-tagless-core" % taglessV),
    macroSettings,
  )
  .settings(commonSettings ++ buildSettings ++ publishSettings)
  .settings(addLibs(vAll, "cats-core"))
  .settings(addCompilerPlugins(vAll, "kind-projector"))
  .settings(addTestLibs(vAll, "scalatest", "cats-free", "cats-effect"))

lazy val `encoder-kryo` = (project in file("encoder-kryo"))
  .settings(
    name := "tagless-redux-encoder-kryo",
    libraryDependencies ++= Seq("com.twitter" %% "chill-bijection" % chillV),
    macroSettings,
  )
  .settings(commonSettings ++ buildSettings ++ publishSettings)
  .settings(simulacrumSettings(vAll))
  .settings(addCompilerPlugins(vAll, "kind-projector"))
  .settings(addTestLibs(vAll, "scalatest", "cats-free", "cats-effect"))
  .dependsOn(`encoder-macros` % "test->test;compile->compile", macros % "test->test")

lazy val `encoder-akka` = (project in file("encoder-akka"))
  .settings(
    name := "tagless-redux-encoder-akka",
    libraryDependencies ++= Seq("com.typesafe.akka" %% "akka-actor" % akkaV),
    macroSettings,
  )
  .settings(commonSettings ++ buildSettings ++ publishSettings)
  .settings(simulacrumSettings(vAll))
  .settings(addCompilerPlugins(vAll, "kind-projector"))
  .settings(addTestLibs(vAll, "scalatest", "cats-free", "cats-effect"))
  .dependsOn(`encoder-macros` % "test->test;compile->compile", macros % "test->test")

lazy val `intellij-ijext` = (project in file("intellij-ijext"))
  .enablePlugins(SbtIdeaPlugin)
  .settings(commonSettings ++ publishSettings)
  .settings(
    name := "tagless-redux-ijext",
    ideaPluginName := name.value,
    ideaBuild := "183.4886.37",
    ideaExternalPlugins += IdeaPlugin.Id("Scala", "org.intellij.scala", None)
  )

lazy val macroSettings: Seq[Def.Setting[_]] = Seq(
  resolvers += Resolver.sonatypeRepo("releases"),
  resolvers += Resolver.bintrayRepo("scalameta", "maven"),
  libraryDependencies +=
    compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
)

lazy val buildSettings = sharedBuildSettings(gh, vAll) ++ Seq(crossScalaVersions := Seq(scalaVersion.value)) ++ scalacAllSettings

lazy val commonSettings = sharedCommonSettings ++ Seq(
  parallelExecution in Test := false,
  scalaVersion := vAll.vers("scalac_2.12"),
  crossScalaVersions := Seq(scalaVersion.value),
  developers := List(
    Developer("Dan Di Spaltro", "@dispalt", "dan.dispaltro@gmail.com", new java.net.URL("http://dispalt.com"))
  )
) ++
  unidocCommonSettings ++
  addCompilerPlugins(vAll, "kind-projector")

lazy val publishSettings = sharedPublishSettings(gh) ++ sharedReleaseProcess
