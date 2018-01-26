# sbt-avro

sbt-avro is a sbt 1.x plugin for generating Scala classes from Apache Avro schemas hosted on a remote Confluent Schema Registry.

## Examples

## Install

Add the plugin to your build, eg in `./project/plugins.sbt` add this line:

```scala
addSbtPlugin("com.iadvize" % "sbt-avro" % "1.0.0-SNAPSHOT")
```

### Download Avro schema files from Schema Registry

By default sbt-avro will download all Avro schema files from local schema registry to your default resources_managed directory (ie: `target/scala-2.12/resources_managed/main/avro/`).
Please check settings section for more information about available settings.

Example:
```scala
sbt avro:download
```

### Upload Avro schema files to Schema Registry

sbt-avro can upload all Avro Schema files from your resources directory (ie: `src/main/resources/avro`) to a local schema registry.
Please check settings section for more information about available settings.

Example:
```scala
sbt avro:upload
```

### Generate scala classes from Avro schema files

The plugin sbt-avro will look for `*.avsc` files in :
- Ressources folder (ie: `src/main/resources/avro/`)
- Ressources Managed folder (ie: `target/scala-2.12/ressources_managed/main/avro/`)

Important : If a duplicate avro schema (same name ) is detected in your ressources folder and in your ressources_managed folder, the compiler will compile only the schema in ressources folder. So, for example, in dev if you want to try some local changes, you can duplicate your schema from your ressources managed folder into your ressources folder and compile your changes.

So put your schema files there and run:

```scala
sbt avro:generate
```

The case classes will get generated in your default src_managed directory (ie:`target/scala-2.12/src_managed/main/avro/`).

## Documentation

### Settings

Name | Default | Description
-----|---------|------------
schemaRegistryEndpoint | ``localhost:8081`` | Schema Registry endpoint
schemas | All Avro schemas with last version) | A list of (subject, version)
resourceManagedDirectory | ``$resourceManaged/main/avro`` | Path containing *.avsc files from schema registry.
sourceManagedDirectory | ``$sourceManaged/main/avro`` | Path for the generated *.scala files
resourceDirectory | ``$resource/main/avro`` | Path containing your *.avs files.
templateDirectory | ``internal`` | Path containing the templates.


#### Tasks

Task                                    | Description
----------------------------------------|----------------------------------------
download                                | Download Avro schemas from a Schema Registry
generate                                | Generate case classes from avro files

Important: `sbt:compile` task will only call avro:generate and not call download task.

#### Custom Templates

You can use your custom templates to generate scala source from Avro schema.
If you want to use custom templates, please add your custom templates into `$templateDirectory` folder.
Template example:
```velocity
#if ($schema.getNamespace())
package $schema.getNamespace()
#end

#if ($schema.getDoc())
/** $schema.getDoc() */
#end
object ${this.mangle($schema.getName())} extends Enumeration {

  val Schema = "${this.javaEscape($schema.toString())}"

  val #foreach ($symbol in ${schema.getEnumSymbols()})${this.mangle($symbol)}#if ($velocityHasNext), #end#end = Value

}

```

## Contribute

Look at contribution guidelines here : [CONTRIBUTING.md](CONTRIBUTING.md)

### Contributors

- [Victor Pirat](https://github.com/atvictor)
- [Jocelyn Dréan](https://github.com/jocelyndrean)
- [Ian Duffy](https://github.com/imduffy15)
