import sbtrelease.ReleasePlugin.autoImport.{ReleaseStep, _}
import sbtrelease.ReleaseStateTransformations._

val scalaV      = "2.13.8"
val taglessV    = "0.12"
val akkaV       = "2.6.19"
val catsV       = "2.7.0"
val boopickleV  = "1.3.1"
val scodecBitsV = "1.1.31"
val scodecCoreV = "1.11.9"
val chillV      = "0.10.0"
val scalaTestV  = "3.2.9"

val deps = Seq(
  "org.scalatestplus" %% "scalatestplus-scalacheck" % "3.1.0.0-RC2",
  "org.typelevel"     %% "cats-core"                % catsV,
  "org.typelevel"     %% "cats-free"                % catsV,
  "org.scalatest"     %% "scalatest"                % scalaTestV
)

lazy val macroAnnotationSettings = Seq(
  resolvers ++= Resolver.sonatypeOssRepos("releases"),
  scalacOptions ++= {
    if (scalaVersion.value == scalaV) Seq("-Ymacro-annotations")
    else Seq("-Xfuture")
  },
  libraryDependencies ++= {
    if (scalaVersion.value == scalaV) Seq.empty
    else Seq(compilerPlugin(("org.scalamacros" % "paradise" % "2.1.1").cross(CrossVersion.full)))
  }
)

ThisBuild / scalaVersion := scalaV
ThisBuild / organization := "com.dispalt"

ThisBuild / intellijPluginName := "tagless-redux-ijext"
ThisBuild / intellijBuild := "223.7571.58"

lazy val root = (project in file("."))
  .settings(noPublishSettings)
  .settings(commonSettings ++ buildSettings ++ publishSettings)
  .aggregate(macros, tests, `encoder-macros`, `encoder-kryo`, `intellij-ijext`, `encoder-akka`, `encoder-boopickle`)

lazy val macros = (project in file("macros"))
  .settings(
    name := "tagless-redux-macros",
    libraryDependencies += "org.typelevel" %% "cats-tagless-macros" % taglessV,
    macroSettings,
    Compile / resourceGenerators += Def.task {
      val rootFolder = (Compile / resourceManaged).value / "META-INF"
      rootFolder.mkdirs()
      val compatFile = rootFolder / "intellij-compat.json"

      IO.write(compatFile, s"""{ "artifact": "com.dispalt % tagless-redux-ijext_2.13 % ${version.value}" }""")

      Seq(compatFile)
    }
  )
  .settings(commonSettings ++ buildSettings ++ publishSettings)
  .settings(libraryDependencies ++= deps)
  .settings(macroAnnotationSettings)

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
  .settings(libraryDependencies ++= deps, macroAnnotationSettings)

lazy val `encoder-kryo` = (project in file("encoder-kryo"))
  .settings(
    name := "tagless-redux-encoder-kryo",
    libraryDependencies ++= Seq("com.twitter" %% "chill-bijection" % chillV),
    macroSettings
  )
  .settings(commonSettings ++ buildSettings ++ publishSettings)
  .settings(libraryDependencies ++= deps, macroAnnotationSettings)
  .dependsOn(`encoder-macros` % "test->test;compile->compile", macros % "test->test")

lazy val `encoder-akka` = (project in file("encoder-akka"))
  .settings(
    name := "tagless-redux-encoder-akka",
    libraryDependencies ++= Seq("com.typesafe.akka" %% "akka-actor" % akkaV),
    macroSettings
  )
  .settings(commonSettings ++ buildSettings ++ publishSettings)
  .settings(libraryDependencies ++= deps, macroAnnotationSettings)
  .dependsOn(`encoder-macros` % "test->test;compile->compile", macros % "test->test")

lazy val `encoder-boopickle` = (project in file("encoder-boopickle"))
  .settings(
    name := "tagless-redux-encoder-boopickle",
    libraryDependencies ++= Seq(
      "io.suzaku"  %% "boopickle"   % boopickleV,
      "org.scodec" %% "scodec-bits" % scodecBitsV,
      "org.scodec" %% "scodec-core" % scodecCoreV
    ),
    macroSettings
  )
  .settings(commonSettings ++ buildSettings ++ publishSettings)
  .settings(libraryDependencies ++= deps, macroAnnotationSettings)
  .dependsOn(`encoder-macros` % "test->test;compile->compile", macros % "test->test")

lazy val `intellij-ijext` = (project in file("intellij-ijext"))
  .enablePlugins(SbtIdeaPlugin)
  .settings(commonSettings ++ publishSettings)
  .settings(
    name := "tagless-redux-ijext",
    intellijPluginName := name.value,
    intellijPlugins += "org.intellij.scala".toPlugin,
    intellijBuild := "223.7571.58",
    packageMethod := PackagingMethod.Standalone(),
    scalaVersion := scalaV,
    crossScalaVersions := Seq(scalaV),
    patchPluginXml := pluginXmlOptions { xml =>
      xml.version    = version.value
      xml.sinceBuild = (ThisBuild / intellijBuild).value
      xml.untilBuild = "231.*"
    },
    Compile / resourceGenerators += Def.task {
      val rootFolder = (Compile / resourceManaged).value / "META-INF"
      rootFolder.mkdirs()
      val fileOut = rootFolder / "intellij-compat.xml"

      IO.write(fileOut, s"""
          |<!DOCTYPE intellij-compat PUBLIC "Plugin/DTD"
          |        "https://raw.githubusercontent.com/JetBrains/intellij-scala/idea183.x/scala/scala-impl/src/org/jetbrains/plugins/scala/components/libextensions/intellij-compat.dtd">
          |<intellij-compat>
          |    <id>dispalt.taglessRedux</id>
          |    <name>Tagless Intellij Support</name>
          |    <description>Provides an autoFunctorK, finalAlg, kryoEncoder, akkaEncoder injector for tagless programs</description>
          |    <version>${version.value}</version>
          |    <vendor>tagless-redux</vendor>
          |    <ideaVersion since-build="2020.3.0" until-build="2023.1.0">
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

lazy val paradisePlugin = Def.setting {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, v)) if v <= 12 =>
      Seq(compilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.patch))
    case _ =>
      // if scala 2.13.0-M4 or later, macro annotations merged into scala-reflect
      // https://github.com/scala/scala/pull/6606
      Nil
  }
}

lazy val macroSettings: Seq[Def.Setting[_]] = Seq(
  resolvers ++= Resolver.sonatypeOssRepos("releases"),
  resolvers += Resolver.bintrayRepo("scalameta", "maven"),
  libraryDependencies ++= paradisePlugin.value
)

lazy val noPublishSettings: Seq[Def.Setting[_]] = Seq(publish / skip := true)

lazy val buildSettings =
  /*sharedBuildSettings(gh, libs) ++*/ Seq(
    scalacOptions ++= Seq(
      "-feature",
      "-deprecation",
      "-encoding",
      "UTF-8",
      "-unchecked",
      "-Xlint",
      //    "-Yno-adapted-args",
      "-Ywarn-dead-code",
      "-Ywarn-numeric-widen",
      "-Ywarn-value-discard",
      "-language:_",
      "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
      "-language:experimental.macros", // Allow macro definition (besides implementation and application)
      "-language:higherKinds", // Allow higher-kinded types
      "-language:implicitConversions" // Allow definition of implicit functions called views
    )
  )

lazy val commonSettings = Seq(
  Test / parallelExecution := false,
  scalaVersion := scalaV,
  crossScalaVersions := Seq(scalaV),
  organization := "com.dispalt.relay", // TODO - Coordinates
  sonatypeProfileName := "com.dispalt", // TODO - Coordinates
  developers := List(
    Developer("Dan Di Spaltro", "@dispalt", "dan.dispaltro@gmail.com", new java.net.URL("http://dispalt.com"))
  ),
  addCompilerPlugin(("org.typelevel" % "kind-projector" % "0.13.2").cross(CrossVersion.full))
)

lazy val publishSettings: Seq[Def.Setting[_]] = /*sharedPublishSettings(gh) ++*/ Seq(
  releaseProcess :=
    Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      releaseStepCommandAndRemaining("+ test"),
      releaseStepCommandAndRemaining(";+ intellij-ijext/updateIntellij ;+ intellij-ijext/test"),
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      releaseStepCommandAndRemaining("+ publishSigned"),
      releaseStepCommandAndRemaining("sonatypeReleaseAll"),
      setNextVersion,
      pushChanges
    )
)
