package com.pschlup.exposedmapping

import com.squareup.kotlinpoet.TypeName
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.postgresql.ds.PGSimpleDataSource
import java.net.URI
import java.sql.ResultSet
import javax.sql.DataSource

@Suppress("unused")
class ExposedMappingPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val extension = project.extensions.create("exposedMapping", ExposedMappingPluginExtension::class.java)

    project.tasks.register("generateExposedMapping") {
      doLast {
        println("****** Generating Exposed mapping classes")

        // Use the configured output directory or default to src/main/kotlin
        val buildPath =
          extension.outputDir?.let { project.file(it) }
            ?: project.layout.projectDirectory
              .file("src/main/kotlin")
              .asFile

        // Ensure the output directory exists
        buildPath.mkdirs()

        // Set the package name for generated models
        if (extension.packageName != null) {
          PACKAGE_NAME = extension.packageName!!
        }

        val dataSource = getDatasource(extension)

        // Generate Enum classes
        val enumSpecs = dataSource.getEnumTypes()
        val enumNames = enumSpecs.map { it.objectName }.toSet()

        enumSpecs.forEach { enumSpec ->
          val sourceCode = generateEnum(enumSpec)
          sourceCode.writeTo(buildPath)
        }

        val tables = dataSource.getTableNames(extension.schemas)
        tables
          .forEach { tableSpec ->
            println("Generating ORM mapping for table ${tableSpec.tableName}")
            val columns = dataSource.getColumnsForTable(tableSpec, enumNames)
            val sourceCode = generateModel(tableSpec.tableName, columns)
            sourceCode.writeTo(buildPath)
          }
      }
    }
//
//    project
//      .task("generateExposedMapping")
//      .doLast {
//        println("****** Generating Exposed mapping classes")
//
//        // Use the configured output directory or default to src/main/kotlin
//        val buildPath =
//          extension.outputDir?.let { project.file(it) }
//            ?: project.layout.projectDirectory
//              .file("src/main/kotlin")
//              .asFile
//
//        // Ensure the output directory exists
//        buildPath.mkdirs()
//
//        // Set the package name for generated models
//        if (extension.packageName != null) {
//          PACKAGE_NAME = extension.packageName!!
//        }
//
//        val dataSource = getDatasource(extension)
//
//        // Generate Enum classes
//        val enumSpecs = dataSource.getEnumTypes()
//        val enumNames = enumSpecs.map { it.objectName }.toSet()
//
//        enumSpecs.forEach { enumSpec ->
//          val sourceCode = generateEnum(enumSpec)
//          sourceCode.writeTo(buildPath)
//        }
//
//        val tables = dataSource.getTableNames(extension.schemas)
//        tables
//          .forEach { tableSpec ->
//            println("Generating ORM mapping for table ${tableSpec.tableName}")
//            val columns = dataSource.getColumnsForTable(tableSpec, enumNames)
//            val sourceCode = generateModel(tableSpec.tableName, columns)
//            sourceCode.writeTo(buildPath)
//          }
//      }
  }

  private fun getDatasource(extension: ExposedMappingPluginExtension): DataSource {
    // Use JDBC URL if provided
    if (extension.jdbcUrl != null) {
      if (extension.jdbcUrl!!.startsWith("jdbc:")) {
        // Standard JDBC URL
        return PGSimpleDataSource().apply {
          setURL(extension.jdbcUrl)
          if (extension.user != null) user = extension.user
          if (extension.password != null) password = extension.password
        }
      } else {
        // Heroku-style URL
        val uri = URI(extension.jdbcUrl!!)
        val credentials = uri.userInfo?.split(":") ?: emptyList()

        return PGSimpleDataSource().apply {
          databaseName = uri.path.substring(1)
          serverNames = arrayOf(uri.host)
          portNumbers = arrayOf(uri.port.takeIf { it != -1 } ?: 5432).toIntArray()
          user = credentials.getOrNull(0) ?: ""
          password = credentials.getOrNull(1) ?: ""
        }
      }
    }

    // Use environment variable if available
    val envUrl = System.getenv("DATABASE_URL")
    if (envUrl != null) {
      val uri = URI(envUrl)
      val credentials = uri.userInfo?.split(":") ?: emptyList()

      return PGSimpleDataSource().apply {
        databaseName = uri.path.substring(1)
        serverNames = arrayOf(uri.host)
        portNumbers = arrayOf(uri.port.takeIf { it != -1 } ?: 5432).toIntArray()
        user = credentials.getOrNull(0) ?: ""
        password = credentials.getOrNull(1) ?: ""
      }
    }

    // Use individual properties
    return PGSimpleDataSource().apply {
      databaseName = extension.databaseName ?: error("No databaseName provided")
      serverNames = arrayOf(extension.serverName ?: "localhost")
      portNumbers = arrayOf(extension.port ?: 5432).toIntArray()
      user = extension.user ?: error("No user provided")
      password = extension.password ?: error("No password provided")
    }
  }
}

private fun DataSource.getColumnsForTable(
  tableSpec: TableSpec,
  enumNames: Set<String>,
): List<ColumnSpec> {
  val foreignKeyMap =
    connection.metaData
      .getImportedKeys(null, tableSpec.schemaName, tableSpec.tableName)
      .toSequence {
        val columnName = getString("fkcolumn_name")
        val otherTable = getString("pktable_name")
        columnName to otherTable
      }.toMap()

  return connection.metaData
    .getColumns(null, tableSpec.schemaName, tableSpec.tableName, null)
    .toSequence {
      val columnName = getString("COLUMN_NAME")
      val isNullable = getBoolean("IS_NULLABLE")
      val typeName =
        getString("TYPE_NAME").let {
          // Handle schema-qualified types
          if (it.contains(".")) {
            it.replace("\"", "").split(".").last()
          } else {
            it
          }
        }

      if (columnName in foreignKeyMap) {
        ColumnSpec.ForeignKey(
          columnName,
          otherTable = foreignKeyMap[columnName]!!,
        )
      } else if (typeName in enumNames) {
        ColumnSpec.EnumColumn(
          name = columnName,
          enumType = typeName,
          enumClass = typeName.toEnumClass(),
          isNullable = isNullable,
        )
      } else {
        ColumnSpec.SimpleColumn(
          name = columnName,
          size = getInt("COLUMN_SIZE"),
          type = typeName,
          isNullable = isNullable,
        )
      }
    }.toList()
}

sealed class ColumnSpec(
  val name: String,
) {
  class SimpleColumn(
    name: String,
    val size: Int? = null,
    val type: String,
    val isNullable: Boolean,
  ) : ColumnSpec(name = name)

  @Suppress("unused")
  class EnumColumn(
    name: String,
    val enumType: String,
    val enumClass: TypeName,
    val isNullable: Boolean,
  ) : ColumnSpec(name = name)

  class ForeignKey(
    name: String,
    val otherTable: String,
  ) : ColumnSpec(name = name)
}

private fun DataSource.getTableNames(schemas: List<String>?): Sequence<TableSpec> {
  val schemasToUse = schemas ?: listOf("public")
  return schemasToUse
    .flatMap { schema ->
      getTableNames(schema)
    }.asSequence()
}

private fun DataSource.getTableNames(schemaName: String): Sequence<TableSpec> {
  return connection.metaData
    .getTables(null, schemaName, "%", arrayOf("TABLE"))
    .toSequence {
      getString("TABLE_NAME")
    }.filter {
      // Ignores Flyway migration state tables
      !it.startsWith("flyway")
    }.map { tableName ->
      TableSpec(schemaName, tableName)
    }
}

private data class TableSpec(
  val schemaName: String,
  val tableName: String,
)

private fun DataSource.getEnumTypes(): List<EnumSpec> {
  // Lists both enum names and values in a single query
  val sql =
    """
    SELECT t.typname, e.enumlabel
    FROM pg_type AS t
       JOIN pg_enum AS e ON t.oid = e.enumtypid
    ORDER BY e.enumsortorder;
    """.trimIndent()

  return connection
    .prepareStatement(sql)
    .executeQuery()
    .toSequence {
      val typeName = getString(1)
      val enumValue = getString(2)
      typeName to enumValue
    }.groupBy({ it.first }, { it.second })
    .map {
      EnumSpec(it.key, it.value)
    }.toList()
}

internal data class EnumSpec(
  val objectName: String,
  val values: List<String>,
)

private fun <T> ResultSet.toSequence(transform: ResultSet.() -> T): Sequence<T> {
  return generateSequence {
    if (next()) {
      transform()
    } else {
      null
    }
  }
}

open class ExposedMappingPluginExtension {
  var packageName: String? = null
  var outputDir: String? = null
  var schemas: List<String>? = null

  // Database configuration
  var jdbcUrl: String? = null
  var serverName: String? = null
  var databaseName: String? = null
  var port: Int? = null
  var user: String? = null
  var password: String? = null
}
