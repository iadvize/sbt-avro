# sbt-avro

## Overview

sbt-avro is a sbt 1.x plugin for generating Scala classes from Apache Avro schemas hosted on a remote Confluent Schema Registry.

## Usage

### Installation

Add the plugin to your build, eg in `./project/plugins.sbt` add this line:

```scala
    addSbtPlugin("com.iadvize" % "sbt-avro" % "1.0.0-SNAPSHOT")
```

### Download Avro schema files from Schema Registry

By default sbt-avro will download all Avro schema files from local schema registry to your default ressources_managed directory (ie: `target/scala-2.12/ressources_managed/main/avro/`). 
Please check settings section for more information about available settings.

Example:
```scala
sbt avro:download
```

### Generate scala classes from Avro schema files

The plugin sbt-avro will look for `*.avsc` files in :
- Ressources folder (ie: `src/main/resources/avro/`)
- Ressources Managed folder (ie: `target/scala-2.12/ressources_managed/main/avro/`)

Important : If a duplicate avro schema (same name ) is detected in your ressources folder and in your ressources_managed folder, the compiler will compile only the schema in ressources folder. So, for example, in dev if you want to try some local changes, you can duplicate your schema from your ressources managed folder into your ressources folder and compile your changes.

So put your schema files there and run:

```scala
sbt avro:compile
```

The case classes will get generated in your default src_managed directory (ie:`target/scala-2.12/src_managed/main/avro/`).


## Settings

Name | Default | Description
-----|---------|------------
schemaRegistryEndpoint | ``localhost:8081`` | Schema Registry endpoint
schemas | all Avro schemas with last version) | (a list of (subject, version)
resourceManagedDirectory | ``$resourceManaged/main/avro`` | Path containing *.avsc files from schema registry.
sourceManagedDirectory | ``$sourceManaged/main/avro`` | Path for the generated *.scala files
resourceDirectory | ``$resource/main/avro`` | Path containing your *.avs files.
templateDirectory | ``internal`` | Path containing the templates.


## Tasks

Task                                    | Description
----------------------------------------|----------------------------------------
download                                | Download Avro schemas from a Schema Registry
generate                                | Generate case classes from avro files
default                                 | avro:download + avro:generate
compile                                 | avro:default + compile



## Custom Templates

## Development

### Testing
