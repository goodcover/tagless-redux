ThisBuild / scalaVersion := "2.12.10"
ThisBuild / organization := "com.dispalt"

val gh = GitHubSettings(org = "dispalt", proj = "tagless-redux", publishOrg = "com.dispalt", license = apache)

val libs = org.typelevel.libraries
  .add("cats", "1.5.0")
  .add("scalatestplus", version = "3.1.0.0-RC2", org = "org.scalatestplus", "scalatestplus-scalacheck")

val taglessV = "0.10"
val akkaV    = "2.5.25"
val chillV   = "0.9.3"

ThisBuild / intellijPluginName := "tagless-redux-ijext"
ThisBuild / intellijBuild := "192.6817.14"

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
    }
  )
  .settings(commonSettings ++ buildSettings ++ publishSettings)
  .settings(libs.dependency("cats-core"))
  .settings(libs.testDependencies("scalatest", "cats-free", "cats-effect"))
  .settings(scalaMacroDependencies(libs))

lazy val tests = (project in file("tests"))
  .settings(commonSettings ++ buildSettings ++ publishSettings)
  .settings(name := "tagless-redux-tests", noPublishSettings, macroSettings)
  .dependsOn(macros % "compile->compile;test->test")

lazy val `encoder-macros` = (project in file("encoder-macros"))
  .settings(
    name := "tagless-redux-encoder-macros",
    libraryDependencies ++= Seq("org.typelevel" %% "cats-tagless-core" % taglessV),
    macroSettings
  )
  .settings(commonSettings ++ buildSettings ++ publishSettings)
  .settings(scalaMacroDependencies(libs))
  .settings(libs.testDependencies("scalatest", "cats-free", "cats-effect"))

lazy val `encoder-kryo` = (project in file("encoder-kryo"))
  .settings(
    name := "tagless-redux-encoder-kryo",
    libraryDependencies ++= Seq("com.twitter" %% "chill-bijection" % chillV),
    macroSettings
  )
  .settings(commonSettings ++ buildSettings ++ publishSettings)
  .settings(libs.testDependencies("scalatest", "cats-free", "cats-effect"), scalaMacroDependencies(libs))
  .dependsOn(`encoder-macros` % "test->test;compile->compile", macros % "test->test")

lazy val `encoder-akka` = (project in file("encoder-akka"))
  .settings(
    name := "tagless-redux-encoder-akka",
    libraryDependencies ++= Seq("com.typesafe.akka" %% "akka-actor" % akkaV),
    macroSettings
  )
  .settings(commonSettings ++ buildSettings ++ publishSettings)
  .settings(libs.testDependencies("scalatest", "cats-free", "cats-effect"), scalaMacroDependencies(libs))
  .dependsOn(`encoder-macros` % "test->test;compile->compile", macros % "test->test")

lazy val `intellij-ijext` = (project in file("intellij-ijext"))
  .enablePlugins(SbtIdeaPlugin)
  .settings(commonSettings ++ publishSettings)
  .settings(
    name := "tagless-redux-ijext",
    intellijPluginName := name.value,
    intellijExternalPlugins += "org.intellij.scala".toPlugin,
    intellijInternalPlugins := Seq("properties", "java", "java-i18n"),
    packageMethod := PackagingMethod.Standalone(),
    patchPluginXml := pluginXmlOptions { xml =>
      xml.version    = version.value
      xml.sinceBuild = (intellijBuild in ThisBuild).value
      xml.untilBuild = "193.*"
    },
    resourceGenerators in Compile += Def.task {
      val rootFolder = (resourceManaged in Compile).value / "META-INF"
      rootFolder.mkdirs()
      val fileOut = rootFolder / "intellij-compat.xml"

      IO.write(fileOut, s"""
          |<!DOCTYPE intellij-compat PUBLIC "Plugin/DTD"
          |        "https://raw.githubusercontent.com/JetBrains/intellij-scala/idea183.x/scala/scala-impl/src/org/jetbrains/plugins/scala/components/libextensions/intellij-compat.dtd">
          |<intellij-compat>
          |    <name>Tagless Intellij Support</name>
          |    <description>Provides an autoFunctorK, finalAlg, kryoEncoder, akkaEncoder injector for tagless programs</description>
          |    <version>${version.value}</version>
          |    <vendor>tagless-redux</vendor>
          |    <ideaVersion since-build="2018.1.0" until-build="2019.4.0">
          |        <extension interface="org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector"
          |                   implementation="com.dispalt.tagless.FunctorKInjector">
          |            <name>Tagless macro support</name>
          |            <description>FunctorK injector</description>
          |        </extension>
          |    </ideaVersion>
          |</intellij-compat>
          """.stripMargin)

      Seq(fileOut)
    }
  )

lazy val macroSettings: Seq[Def.Setting[_]] = Seq(
  resolvers += Resolver.sonatypeRepo("releases"),
  resolvers += Resolver.bintrayRepo("scalameta", "maven"),
  libraryDependencies +=
    compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
)

lazy val buildSettings = sharedBuildSettings(gh, libs) ++ Seq(crossScalaVersions := Seq(scalaVersion.value)) ++ scalacAllSettings

lazy val commonSettings = sharedCommonSettings ++ Seq(
  parallelExecution in Test := false,
  scalaVersion := libs.vers("scalac_2.12"),
  crossScalaVersions := Seq(scalaVersion.value),
  developers := List(
    Developer("Dan Di Spaltro", "@dispalt", "dan.dispaltro@gmail.com", new java.net.URL("http://dispalt.com"))
  )
) ++
  unidocCommonSettings ++
  addCompilerPlugins(libs, "kind-projector")

lazy val publishSettings = sharedPublishSettings(gh) ++ sharedReleaseProcess

lazy val ideaRunner = createRunnerProject(`intellij-ijext`, "intellij-ijext-runner")
