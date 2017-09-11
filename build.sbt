sbtPlugin := true

name := "sbt-avro"

organization := "com.iadvize"

scalaVersion := "2.12.3"

publishTo := Some("Artifactory Realm" at "https://iadvize.jfrog.io/iadvize/iadvize-sbt")

credentials += Credentials("Artifactory Realm", "iadvize.jfrog.io", sys.env.getOrElse("ARTIFACTORY_USERNAME", sys.props.getOrElse("ARTIFACTORY_USERNAME", "")), sys.env.getOrElse("ARTIFACTORY_PASS", sys.props.getOrElse("ARTIFACTORY_PASS", "")))

resolvers += "confluent" at "http://packages.confluent.io/maven"

libraryDependencies ++= Seq(
  "org.apache.avro" % "avro-compiler"                % "1.8.2",
  "io.confluent"    % "kafka-schema-registry-client" % "3.3.0"
)
