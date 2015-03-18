import org.allenai.plugins.CoreDependencies._

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

