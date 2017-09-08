sbtPlugin := true

name := "sbt-avro"

organization := "com.iadvize"

scalaVersion := "2.12.3"

resolvers += "confluent" at "http://packages.confluent.io/maven"

libraryDependencies ++= Seq(
  "org.apache.avro" % "avro-compiler"                % "1.8.2",
  "io.confluent"    % "kafka-schema-registry-client" % "3.3.0"
)
