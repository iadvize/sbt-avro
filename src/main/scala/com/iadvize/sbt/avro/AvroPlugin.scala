package com.iadvize.sbt.avro

import java.io.File

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import java.nio.file.{Files, Path, Paths}

import org.apache.avro
import org.apache.avro.Schema.Parser
import org.apache.avro.compiler.specific.SpecificCompiler

import scala.collection.JavaConverters._
import sbt._
import sbt.Keys._
import sbt.internal.util.ManagedLogger

import scala.util.Try

case class Version(version: Int)

object Version {

  val Last = Version(-1)

}

case class Schema(subject: String, version: Version)

class AvroCompiler(schema: avro.Schema) extends SpecificCompiler(schema) {

  val queueField = {
    val field = classOf[SpecificCompiler].getDeclaredField("queue")
    field.setAccessible(true)
    field
  }

  val compileMethod = {
    val method = classOf[SpecificCompiler].getDeclaredMethod("compile", classOf[avro.Schema])
    method.setAccessible(true)
    method
  }

  val writeToDestinationMethod = {
    val method = classOf[SpecificCompiler].getDeclaredClasses.find(_.getSimpleName == "OutputFile").get.getDeclaredMethod("writeToDestination", classOf[File], classOf[File])
    method.setAccessible(true)
    method
  }

  val NullSchema = avro.Schema.create(avro.Schema.Type.NULL)

  override def javaUnbox(schema: avro.Schema) = {
    schema.getType match {
      case avro.Schema.Type.ARRAY => "Array[" + javaUnbox(schema.getElementType) + "]"
      case avro.Schema.Type.BOOLEAN => "Boolean"
      case avro.Schema.Type.BYTES => "Array[Byte]"
      case avro.Schema.Type.DOUBLE => "Double"
      case avro.Schema.Type.ENUM => SpecificCompiler.mangle(schema.getFullName) + ".Value"
      case avro.Schema.Type.FIXED => SpecificCompiler.mangle(schema.getFullName)
      case avro.Schema.Type.FLOAT => "Float"
      case avro.Schema.Type.INT => "Int"
      case avro.Schema.Type.LONG => "Long"
      case avro.Schema.Type.MAP => "Map[String, " + javaUnbox(schema.getValueType) + "]"
      case avro.Schema.Type.NULL => "java.lang.Void"
      case avro.Schema.Type.RECORD => SpecificCompiler.mangle(schema.getFullName)
      case avro.Schema.Type.STRING => "String"
      case avro.Schema.Type.UNION =>
        val types = schema.getTypes.asScala
        if (types.size == 2 && types.contains(NullSchema))
          "Option[" + javaUnbox(if (types(0) == NullSchema) types(1) else types(0)) + "]"
        else
          "Any"
    }
  }

  def compileToDestinationWithResult(src: File, dst: File) =
    queueField.get(this).asInstanceOf[java.util.Set[avro.Schema]].asScala.map(schema => writeToDestinationMethod.invoke(compileMethod.invoke(this, schema), src, dst).asInstanceOf[File])

}

object AvroPlugin extends AutoPlugin {

  object autoImport {

    lazy val Avro = config("avro") extend Compile

    val download = taskKey[Seq[File]]("Download schemas from the registry.")
    val upload = taskKey[Unit]("Upload schemas to thee registry")
    val generate = taskKey[Seq[File]]("Generate Scala classes from schemas.")

    val schemaRegistryEndpoint = settingKey[String]("Schema registry endpoint, defaults to http://localhost:8081.")
    val schemas = settingKey[Seq[Schema]]("List of schemas to download, an empty list will download latest version of all schemas, defaults to an empty list.")
    val directoryName = settingKey[String]("Name of the directories which will contain Avro files, defaults to avro.")
    val classPathTemplatesDirectory = settingKey[String]("Name of the directory containing the templates used to generate the Scala files, defaults to /template/avro/.")

  }

  import autoImport._

  lazy val baseAvroSettings = Seq(
    directoryName := "avro",
    classPathTemplatesDirectory := "/template/avro/",
    schemaRegistryEndpoint := "http://localhost:8081",
    schemas := Seq.empty[Schema],
    resourceManaged := (resourceManaged in Compile).value / directoryName.value,
    resourceDirectory := (resourceDirectory in Compile).value / directoryName.value,
    sourceManaged := (sourceManaged in Compile).value / directoryName.value,
    download := downloadTask.value,
    upload := uploadTask.value,
    generate := generateTask.value,

    resourceGenerators in Compile += download.taskValue,
    managedResourceDirectories in Compile += (resourceManaged in Avro).value,

    sourceGenerators in Compile += generate.taskValue,
    managedSourceDirectories in Compile += (sourceManaged in Avro).value,

    cleanFiles ++= Seq(sourceManaged.value, resourceManaged.value)
  )

  lazy val downloadTask = Def.task {
    val logger = streams.value.log

    val configuredSchemas = schemas.value
    val configuredResourceManaged = resourceManaged.value
    val configuredSchemaRegistryEndpoint = schemaRegistryEndpoint.value

    val schemaRegistryClient = new CachedSchemaRegistryClient(configuredSchemaRegistryEndpoint, 10000)

    val schemasToDownload = if (configuredSchemas.isEmpty) {
      schemaRegistryClient.getAllSubjects.asScala.map(subject => Schema(subject, Version.Last))
    } else configuredSchemas

    logger.info("About to download:\n" + schemasToDownload.map(schema => "  " + schema.subject + " " + (if (schema.version == Version.Last) "latest" else "v" + schema.version)).mkString("\n") + "\nfrom " + configuredSchemaRegistryEndpoint)

    Files.createDirectories(configuredResourceManaged.toPath)

    schemasToDownload
      .map {
        case Schema(subject, Version.Last) => (subject, schemaRegistryClient.getLatestSchemaMetadata(subject))
        case Schema(subject, Version(version)) => (subject, schemaRegistryClient.getSchemaMetadata(subject, version))
      }
      .map { case (subject, schema) =>
        val path = Paths.get(configuredResourceManaged.absolutePath, subject + ".avsc")
        val writer = Files.newBufferedWriter(path)
        writer.write(schema.getSchema)
        writer.close()
        path.toFile
      }
      .toSeq
  }

  lazy val uploadTask = Def.task {
    val logger = streams.value.log

    val configuredSchemaRegistryEndpoint = schemaRegistryEndpoint.value
    val schemaRegistryClient = new CachedSchemaRegistryClient(configuredSchemaRegistryEndpoint, 10000)

    val schemasToRegister = parseSchemas(logger, resourceDirectory.value.toPath)

    schemasToRegister.foreach {
      case(subject: String, (file: File, schema: avro.Schema)) =>
        logger.info(s"Calling register $subject ${file.getAbsolutePath}")
        schemaRegistryClient.register(subject, schema)
    }
  }

  lazy val generateTask = Def.task {
    val logger = streams.value.log

    val configuredSourceManaged = sourceManaged.value

    val schemasToGenerate = parseSchemas(logger, resourceManaged.value.toPath) ++
      parseSchemas(logger, resourceDirectory.value.toPath)

    Files.createDirectories(configuredSourceManaged.toPath)

    schemasToGenerate.values.flatMap { case (schemaFile, schema) =>
      val compiler = new AvroCompiler(schema)
      compiler.setSuffix(".scala")
      compiler.setTemplateDir(classPathTemplatesDirectory.value)
      compiler.compileToDestinationWithResult(schemaFile, configuredSourceManaged).toSeq
    }.toSeq
  }

  private def parseSchemas(logger: ManagedLogger, path: Path) = {
    val parser = new Parser()
    if (path.toFile.exists())
      Files.newDirectoryStream(path, "*.avsc").iterator().asScala.flatMap { schemaPath =>
        Try(parser.parse(schemaPath.toFile)).fold(throwable => {
          logger.error(s"Can't parse schema $schemaPath, got error: ${throwable.getMessage}"); None
        }, schema => Some(schema.getName -> (schemaPath.toFile, schema)))
      }.toMap
    else Map.empty[String, (File, avro.Schema)]
  }

  override def requires = sbt.plugins.JvmPlugin

  override def trigger = allRequirements

  override val projectSettings = inConfig(Avro)(baseAvroSettings)

}
