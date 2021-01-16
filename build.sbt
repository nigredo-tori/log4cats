import sbtcrossproject.{crossProject, CrossType}

val Scala213 = "2.13.4"
val Scala212 = "2.12.12"

enablePlugins(SonatypeCiReleasePlugin)

ThisBuild / organization := "org.typelevel"
ThisBuild / baseVersion := "1.2"
ThisBuild / crossScalaVersions := Seq(Scala213, Scala212)
ThisBuild / scalaVersion := Scala213
ThisBuild / publishFullName := "Christopher Davenport"
ThisBuild / publishGithubUser := "christopherdavenport"

ThisBuild / versionIntroduced := Map(
  // First versions after the Typelevel move
  "2.12" -> "1.1.2",
  "2.13" -> "1.1.2",
  "3.0.0-M2" -> "1.1.2",
  "3.0.0-M3" -> "1.1.2",
)

ThisBuild / githubWorkflowSbtCommand := "csbt"

ThisBuild / githubWorkflowJavaVersions := Seq("adopt@1.8", "adopt@1.11")

val MicrositesCond = s"matrix.scala == '$Scala212'"

ThisBuild / githubWorkflowBuild := Seq(
  WorkflowStep.Sbt(List("test"), name = Some("Test")),
  WorkflowStep.Sbt(List("mimaReportBinaryIssues"), name = Some("Binary Compatibility Check"))
)

def micrositeWorkflowSteps(cond: Option[String] = None): List[WorkflowStep] = List(
  WorkflowStep.Use(
    "ruby",
    "setup-ruby",
    "v1",
    params = Map("ruby-version" -> "2.6"),
    cond = cond
  ),
  WorkflowStep.Run(List("gem update --system"), cond = cond),
  WorkflowStep.Run(List("gem install sass"), cond = cond),
  WorkflowStep.Run(List("gem install jekyll -v 4"), cond = cond)
)

ThisBuild / githubWorkflowAddedJobs ++= Seq(
  WorkflowJob(
    "scalafmt",
    "Scalafmt",
    githubWorkflowJobSetup.value.toList ::: List(
      WorkflowStep.Sbt(List("scalafmtCheckAll"), name = Some("Scalafmt"))
    ),
    scalas = crossScalaVersions.value.toList
  ),
  WorkflowJob(
    "microsite",
    "Microsite",
    githubWorkflowJobSetup.value.toList ::: (micrositeWorkflowSteps(None) :+ WorkflowStep
      .Sbt(List("docs/makeMicrosite"), name = Some("Build the microsite"))),
    scalas = List(Scala212)
  )
)

ThisBuild / githubWorkflowTargetTags ++= Seq("v*")
ThisBuild / githubWorkflowPublishTargetBranches := Seq(RefPredicate.StartsWith(Ref.Tag("v")))

ThisBuild / githubWorkflowPublish := Seq(
  WorkflowStep.Sbt(
    List("release"),
  )
) ++ micrositeWorkflowSteps(Some(MicrositesCond)).toSeq :+ WorkflowStep.Sbt(
  List("docs/publishMicrosite"),
  cond = Some(MicrositesCond)
)

val catsV = "2.3.1"
val catsEffectV = "2.3.1"
val slf4jV = "1.7.30"
val specs2V = "4.10.5"
val logbackClassicV = "1.2.3"

Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val log4cats = project
  .in(file("."))
  .aggregate(
    coreJVM,
    coreJS,
    testingJVM,
    testingJS,
    noopJVM,
    noopJS,
    slf4j,
    docs
  )
  .enablePlugins(NoPublishPlugin)
  .settings(commonSettings, releaseSettings)

lazy val docs = project
  .settings(commonSettings, micrositeSettings)
  .enablePlugins(MicrositesPlugin)
  .enablePlugins(MdocPlugin)
  .enablePlugins(NoPublishPlugin)
  .settings(mdocIn := sourceDirectory.value / "main" / "mdoc")
  .dependsOn(slf4j)

lazy val core = crossProject(JSPlatform, JVMPlatform)
  .settings(commonSettings, releaseSettings)
  .settings(
    name := "log4cats-core",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core" % catsV
    )
  )
lazy val coreJVM = core.jvm
lazy val coreJS = core.js

lazy val testing = crossProject(JSPlatform, JVMPlatform)
  .settings(commonSettings, releaseSettings)
  .dependsOn(core)
  .settings(
    name := "log4cats-testing",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-effect" % catsEffectV
    )
  )
lazy val testingJVM = testing.jvm
lazy val testingJS = testing.js

lazy val noop = crossProject(JSPlatform, JVMPlatform)
  .settings(commonSettings, releaseSettings)
  .dependsOn(core)
  .settings(
    name := "log4cats-noop"
  )
lazy val noopJVM = noop.jvm
lazy val noopJS = noop.js

lazy val slf4j = project
  .settings(commonSettings, releaseSettings)
  .dependsOn(coreJVM)
  .settings(
    name := "log4cats-slf4j",
    libraryDependencies ++= Seq(
      "org.slf4j"                       % "slf4j-api"       % slf4jV,
      "org.scala-lang"                  % "scala-reflect"   % scalaVersion.value,
      "org.typelevel" %%% "cats-effect" % catsEffectV,
      "ch.qos.logback"                  % "logback-classic" % logbackClassicV % Test
    )
  )

lazy val contributors = Seq(
  "ChristopherDavenport" -> "Christopher Davenport",
  "lorandszakacs" -> "Loránd Szakács"
)

lazy val commonSettings = Seq(
  libraryDependencies ++= Seq(
    "org.specs2" %%% "specs2-core" % specs2V % Test
  )
)

lazy val releaseSettings = {
  Seq(
    publishArtifact in Test := false,
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/typelevel/log4cats"),
        "git@github.com:typelevel/log4cats.git"
      )
    ),
    homepage := Some(url("https://github.com/typelevel/log4cats")),
    licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html")),
    pomExtra := {
      <developers>
        {
        for ((username, name) <- contributors)
          yield <developer>
          <id>{username}</id>
          <name>{name}</name>
          <url>http://github.com/{username}</url>
        </developer>
      }
      </developers>
    }
  )
}

lazy val micrositeSettings = Seq(
  micrositeName := "log4cats",
  micrositeDescription := "Functional Logging",
  micrositeAuthor := "Christopher Davenport",
  micrositeGithubOwner := "typelevel",
  micrositeGithubRepo := "log4cats",
  micrositeBaseUrl := "/log4cats",
  micrositeDocumentationUrl := "https://typelevel.github.io/log4cats",
  micrositeFooterText := None,
  micrositeHighlightTheme := "atom-one-light",
  micrositePalette := Map(
    "brand-primary" -> "#3e5b95",
    "brand-secondary" -> "#294066",
    "brand-tertiary" -> "#2d5799",
    "gray-dark" -> "#49494B",
    "gray" -> "#7B7B7E",
    "gray-light" -> "#E5E5E6",
    "gray-lighter" -> "#F4F3F4",
    "white-color" -> "#FFFFFF"
  ),
  scalacOptions --= Seq(
    "-Xfatal-warnings",
    "-Ywarn-unused-import",
    "-Ywarn-numeric-widen",
    "-Ywarn-dead-code",
    "-Ywarn-unused:imports",
    "-Xlint:-missing-interpolator,_"
  ),
  micrositePushSiteWith := GitHub4s,
  micrositeGithubToken := sys.env.get("GITHUB_TOKEN")
)
