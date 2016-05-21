name := """ElasticityTest"""
organization := "ItsMeijers"
version := "0.1"

scalaVersion := "2.11.8"

scalacOptions := Seq(
  "-unchecked",
  "-deprecation",
  "-encoding",
  "utf8",
  "-feature",
  "-language:postfixOps")

val akkaVersion = "2.4.3"

libraryDependencies ++= Seq(
  "com.typesafe.akka"    %% "akka-actor"        % akkaVersion,
  "com.typesafe.akka"    %% "akka-http-core"    % akkaVersion,
  "com.typesafe.akka"    %% "akka-http-testkit" % akkaVersion,
  "com.typesafe.akka"    %% "akka-slf4j"        % akkaVersion,
  "com.typesafe.akka"    %% "akka-http-spray-json-experimental" % akkaVersion,
  "com.typesafe.akka"    %% "akka-testkit"      % akkaVersion % "test",
  "org.scalatest"        %% "scalatest"         % "2.2.6"     % "test",
  "ch.qos.logback"       % "logback-classic"    % "1.0.9",
  "org.typelevel"        %% "cats"              % "0.4.1",
  "com.github.tototoshi" %% "scala-csv"         % "1.3.1",
  "joda-time"            % "joda-time"          % "2.9.3",
  "org.joda"             % "joda-convert"       % "1.8.1",
  "ch.megard"            %% "akka-http-cors"    % "0.1.2")
