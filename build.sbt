import com.typesafe.sbt.packager.Keys.dockerBaseImage

name := "todo-with-play"

addCommandAlias("fixAll", "scalafixAll; scalafmtAll; scalafmtSbt")
addCommandAlias("checkAll", "scalafixAll --check; scalafmtCheckAll; scalafmtSbtCheck")

lazy val commonSettings = Seq(
  version := "0.1.0-SNAPSHOT",
  scalaVersion := "2.13.5",
  scalacOptions ++= List(
    "-deprecation",
    "-feature",
    "-unchecked",
    "-language:implicitConversions",
    "-Yrangepos",
    "-Ymacro-annotations",
    "-Ywarn-unused",
    "-Xlint",
    "-Xfatal-warnings"
  ),
  libraryDependencies ++= {
    val circeVersion = "0.13.0"
    Seq(
      "org.typelevel"     %% "cats-core"       % "2.6.0",
      "io.monix"          %% "monix"           % "3.3.0",
      "io.circe"          %% "circe-core"      % circeVersion,
      "io.circe"          %% "circe-generic"   % circeVersion,
      "io.circe"          %% "circe-parser"    % circeVersion,
      "org.typelevel"     %% "simulacrum"      % "1.0.0",
      "org.scalatest"     %% "scalatest"       % "3.2.8"   % "test",
      "org.scalatestplus" %% "scalacheck-1-15" % "3.2.8.0" % "test",
      "org.scalacheck"    %% "scalacheck"      % "1.15.4"  % "test"
    )
  },
  // scalafix
  addCompilerPlugin(scalafixSemanticdb),
  semanticdbEnabled := true,
  semanticdbVersion := scalafixSemanticdb.revision
)

lazy val domain = (project in file("ptm-domain"))
  .settings(commonSettings)

lazy val usecase = (project in file("ptm-usecase"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.google.inject" % "guice" % "4.2.3"
    )
  )
  .dependsOn(domain)

lazy val infrastructure = (project in file("ptm-infrastructure"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.springframework.security" % "spring-security-web"            % "5.4.6",
      "net.debasishg"               %% "redisclient"                    % "3.30",
      "mysql"                        % "mysql-connector-java"           % "8.0.24",
      "org.flywaydb"                 % "flyway-core"                    % "7.8.2",
      "org.scalikejdbc"             %% "scalikejdbc"                    % "3.5.0",
      "org.scalikejdbc"             %% "scalikejdbc-config"             % "3.5.0",
      "org.scalikejdbc"             %% "scalikejdbc-play-dbapi-adapter" % "2.8.0-scalikejdbc-3.5",
      "org.scalikejdbc"             %% "scalikejdbc-test"               % "3.5.+" % "test",
      "com.typesafe"                 % "config"                         % "1.4.1" % Test
    )
  )
  .dependsOn(domain, usecase)

lazy val http = (project in file("ptm-http"))
  .enablePlugins(PlayScala)
  .enablePlugins(FlywayPlugin)
  .enablePlugins(DockerPlugin)
  .settings(commonSettings)
  .settings(
    name := "ptm-app",
    scalacOptions += s"-Wconf:src=${target.value}/.*:s",
    libraryDependencies ++= Seq(
      guice,
      jdbc,
      "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test
    ),
    TwirlKeys.templateImports := Seq.empty,
    Test / javaOptions += "-Dconfig.file=conf/test.conf"
  )
  .settings(
    flywayUrl := {
      val host = sys.env.getOrElse("DATABASE_HOST", "127.0.0.1")
      val port = sys.env.getOrElse("DATABASE_PORT", "33060")
      s"jdbc:mysql://$host:$port/ptm"
    },
    flywayUser := "ptm",
    flywayPassword := "ptm",
    flywayLocations += "filesystem:database/migration"
  )
  .settings(
    PlayKeys.playRunHooks ++= Seq(
      DockerComposeRunHook(baseDirectory.value),
      FrontendRunHook(baseDirectory.value.getParentFile / "ptm-ui")
    )
  )
  .settings(
    Docker / packageName := name.value,
    dockerBaseImage := "openjdk:8-slim",
    dockerExposedPorts ++= Seq(9000),
    Universal / javaOptions ++= Seq(
      "-Dpidfile.path=/dev/null",
      "-Dconfig.file=/opt/docker/conf/docker.conf"
    )
  )
  .dependsOn(domain, infrastructure, usecase)

lazy val root = (project in file("."))
  .settings(commonSettings)
  .aggregate(domain, infrastructure, usecase, http)

ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.5.0"

Global / onChangedBuildSource := ReloadOnSourceChanges
