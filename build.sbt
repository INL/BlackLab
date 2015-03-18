import org.allenai.plugins.CoreDependencies._
import sbtrelease._
import sbtrelease.ReleasePlugin.ReleaseKeys._

name := "BlackLab"

libraryDependencies ++= Seq(
  "org.scalacheck" %% "scalacheck" % "1.12.0" % Test,
  "com.novocode" % "junit-interface" % "0.11" % Test,
  "org.apache.lucene" % "lucene-core" % "4.2.1",
  "org.apache.lucene" % "lucene-queryparser" % "4.2.1",
  "org.apache.lucene" % "lucene-highlighter" % "4.2.1",
  "org.apache.lucene" % "lucene-queries" % "4.2.1",
  "org.apache.lucene" % "lucene-analyzers-common" % "4.2.1",
  "tomcat" % "jasper-runtime" % "5.5.23",
  "tomcat" % "jsp-api" % "5.5.23",
  "tomcat" % "servlet-api" % "5.5.23",
  Logging.logbackClassic,
  Logging.logbackCore,
  Logging.slf4jApi,
  "org.slf4j" % "log4j-over-slf4j" % Logging.slf4jVersion)

organization := "nl.inl"

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

licenses := Seq("Apache 2.0" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt"))

homepage := Some(url("https://github.com/allenai/BlackLab"))

scmInfo := Some(ScmInfo(
  url("https://github.com/allenai/BlackLab"),
  "https://github.com/allenai/BlackLab.git"))

ReleaseKeys.publishArtifactsAction := PgpKeys.publishSigned.value

pomExtra :=
  <developers>
    <developer>
      <id>allenai-dev-role</id>
      <name>Allen Institute for Artificial Intelligence</name>
      <email>dev-role@allenai.org</email>
    </developer>
  </developers>

PublishTo.sonatype

releaseSettings

releaseVersion := { ver =>
  val snapshot = "(.*-ALLENAI-\\d+)-SNAPSHOT".r
  ver match {
    case snapshot(v) => v
    case _ => versionFormatError
  }
}

nextVersion := { ver =>
  val release = "(.*-ALLENAI)-(\\d+)".r
  // pattern matching on Int
  object Int {
    def unapply(s: String): Option[Int] = try {
      Some(s.toInt)
    } catch {
      case _: java.lang.NumberFormatException => None
    }
  }

  ver match {
    case release(prefix, Int(number)) => s"$prefix-${number+1}-SNAPSHOT"
    case _ => versionFormatError
  }
}

javacOptions in doc := Seq(
  "-source", "1.7",
  "-group", "Core", "nl.inl.blacklab.search:nl.inl.blacklab.search.*:nl.inl.blacklab.tools:nl.inl.blacklab.index:nl.inl.blacklab.index.*:nl.inl.blacklab.highlight:nl.inl.blacklab.queryParser.*:nl.inl.blacklab.perdocument",
  "-group", "Examples and tests", "nl.inl.blacklab.example:nl.inl.blacklab.indexers.*",
  "-group", "Supporting classes", "nl.inl.blacklab.filter:nl.inl.blacklab.forwardindex:nl.inl.blacklab.externalstorage:nl.inl.blacklab.suggest:nl.inl.util",
  "-Xdoclint:none")
