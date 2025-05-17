package com.exposed.mapping

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import java.util.*
import javax.money.MonetaryAmount
import kotlin.time.Duration

// Default package name for generated models - can be overridden via plugin configuration
internal var PACKAGE_NAME = "com.example.model"

internal fun generateEnum(enumSpec: EnumSpec): FileSpec {
  val className = enumSpec.objectName.toEnumClass()

  return FileSpec.builder(className) {
    addType(
      TypeSpec.enumBuilder(className) {
        addSuperinterface(ClassName(PACKAGE_NAME, "DbEnum"))
        primaryConstructor(
          FunSpec.constructorBuilder {
            addParameter("value", String::class)
          },
        )
        addProperty(
          PropertySpec.builder("value", String::class, KModifier.OVERRIDE) {
            initializer("value")
          },
        )
        for (value in enumSpec.values) {
          addEnumConstant(
            value.uppercase(Locale.US),
            TypeSpec.anonymousClassBuilder {
              addSuperclassConstructorParameter("%S", value)
            },
          )
        }

        addType(
          TypeSpec.companionObjectBuilder {
            addFunction(
              FunSpec.builder("of") {
                addParameter("v", String::class)
                returns(className)
                beginControlFlow("return when(%L)", "v")
                for (v in enumSpec.values.toSet()) {
                  addStatement("%S -> %N", v, v.uppercase())
                }
                addStatement("else -> error(\"Invalid '%L' value '\${%L}'\")", enumSpec.objectName, "v")
                endControlFlow()
              },
            )
          },
        )
      },
    )
  }
}

internal fun generateModel(
  tableName: String,
  columns: List<ColumnSpec>,
): FileSpec {
  // Capitalizes the name of the class and makes the name singular as opposed to plural
  val className = tableName.toModelClass()

  val instanceProperties =
    columns
      .filter { it.name != "id" }
      // TODO: Implement database translations
      // Skips translation and currency special fields
      .filter { !it.name.endsWith("_t") && !it.name.endsWith("_c") }
      .map { column ->
        when (column) {
          is ColumnSpec.SimpleColumn ->
            PropertySpec.builder(
              name = column.name.toCamelCase(),
              type = column.toKotlinType(),
            ) {
              mutable(true)
              delegate("%L.%L", "Table", column.name.toCamelCase())
            }

          is ColumnSpec.EnumColumn ->
            PropertySpec.builder(
              name = column.name.toCamelCase(),
              type = column.enumClass,
            ) {
              mutable(true)
              delegate("%L.%L", "Table", column.name.toCamelCase())
            }

          is ColumnSpec.ForeignKey -> {
            // e.g. var account by AccountModel referencedOn Table.accountId
            PropertySpec.builder(
              name = column.name.removeSuffix("_id").toCamelCase(),
              type = column.otherTable.toModelClass(),
            ) {
              mutable(true)
              delegate(
                "%L.BaseDao() referencedOn %L.%L",
                column.otherTable.toModelClassName(),
                "Table",
                column.name.toCamelCase(),
              )
            }
          }
        }
      }

  return FileSpec.builder(className) {
    addImport("org.jetbrains.exposed.sql", "insert", "update")
    addImport(PACKAGE_NAME, "PgEnumValue")
    addType(
      TypeSpec.classBuilder(className) {
        superclass(IntEntity::class)
        addSuperclassConstructorParameter("%L", "id")
        primaryConstructor(
          FunSpec.constructorBuilder {
            addParameter("id", intEntityIdType)
          },
        )
        for (property in instanceProperties) {
          addProperty(property)
        }
        addType(generateDao(className))
        addType(generateTableObject(tableName, columns))
      },
    )
  }
}

/** Generates a nested object called Table containing all column definitions. */
private fun generateTableObject(
  className: String,
  columns: List<ColumnSpec>,
): TypeSpec {
  val tableProperties =
    columns
      // Excludes the ID column which is automatically present in the parent class
      .filter { it.name != "id" }
      // TODO: Implement database translations
      // Skips translation and currency special fields
      .filter { !it.name.endsWith("_t") && !it.name.endsWith("_c") }
      .map { column ->
        when (column) {
          is ColumnSpec.SimpleColumn ->
            PropertySpec.builder(
              name = column.name.toCamelCase(),
              type = column.toColumnType(),
            ) {
              initializer(column.toInitializerBlock())
            }

          is ColumnSpec.EnumColumn ->
            PropertySpec.builder(
              name = column.name.toCamelCase(),
              type = columnType.parameterizedBy(column.enumClass),
            ) {
              initializer(column.toInitializerBlock())
            }

          is ColumnSpec.ForeignKey -> {
            PropertySpec.builder(
              name = column.name.toCamelCase(),
              type = columnType.parameterizedBy(EntityID::class.asClassName().parameterizedBy(Int::class.asClassName())),
            ) {
              initializer("reference(%S, %T.Table)", column.name, column.otherTable.toModelClass())
            }
          }
        }
      }

  return TypeSpec.objectBuilder("Table") {
    superclass(IntIdTable::class)
    addSuperclassConstructorParameter(CodeBlock.of("name = %S", className))
    for (property in tableProperties) {
      addProperty(property)
    }
  }
}

/** Generates an abstract DAO class providing access to standard ORM functions like insert() and update() */
private fun generateDao(className: ClassName): TypeSpec {
  return TypeSpec.classBuilder("BaseDao") {
    addModifiers(KModifier.OPEN)
    superclass(IntEntityClass::class.asClassName().parameterizedBy(className))
    addSuperclassConstructorParameter("%L", "Table")
    addSuperclassConstructorParameter("%L::class.java", className)
    addSuperclassConstructorParameter("{ %L(it) }", className)
    addFunction(
      // fun insert(block: InsertStatement<*>.() -> Unit) {...}
      FunSpec.builder("insert") {
        addModifiers(KModifier.OPEN)
        addParameter(
          name = "block",
          type =
            LambdaTypeName.get(
              receiver = InsertStatement::class.asTypeName().parameterizedBy(Number::class.asTypeName()),
              returnType = Unit::class.asTypeName(),
            ),
        )
        addCode("%L.insert { block.invoke(it) }", "Table")
      },
    )
    addFunction(
      // fun update(id: Int, block: UpdateStatement.() -> Unit) {...}
      FunSpec.builder("update") {
        addModifiers(KModifier.OPEN)
        addParameter("id", Int::class.asTypeName())
        addParameter(
          name = "block",
          type =
            LambdaTypeName.get(
              receiver = UpdateStatement::class.asTypeName(),
              returnType = Unit::class.asTypeName(),
            ),
        )
        addCode("%L.update({ %L.id eq id }) { block.invoke(it) }", "Table", "Table")
      },
    )
  }
}

private fun ColumnSpec.SimpleColumn.toColumnType(): ParameterizedTypeName {
  return when (type) {
    // Composite columns as defined in PostgreSQL don't really work with Exposed in this way
//    "money" -> CompositeMoneyColumn::class.asClassName()
//      .parameterizedBy(
//        BigDecimal::class.asClassName(),
//        CurrencyUnit::class.asClassName(),
//        MonetaryAmount::class.asClassName(),
//      )
//    "monetary_amount" -> CompositeColumn::class.asClassName()
//      .parameterizedBy(MonetaryAmount::class.asClassName())
    else -> columnType.parameterizedBy(toKotlinType())
  }
}

/** Converts a table name to a reference to the class of its corresponding model, e.g. "account" => "AccountModel" */
private fun String.toModelClass() =
  ClassName(
    packageName = PACKAGE_NAME,
    this.toModelClassName(),
  )

internal fun String.toEnumClass(): ClassName =
  ClassName(
    packageName = PACKAGE_NAME,
    this.toEnumClassName(),
  )

internal fun String.toEnumClassName() =
  this.toCamelCase().capitalize()

/** Converts a table name to the name of its corresponding model, e.g. "account" => "AccountModel" */
private fun String.toModelClassName() =
  capitalize().removeSuffix("s") + "Model"

internal fun String.capitalize() =
  snakeToCamelCase().replaceFirstChar { it.titlecase(Locale.US) }

private fun String.snakeToCamelCase(): String {
  val pattern = "_([a-z])".toRegex()
  return replace(pattern) { it.groupValues[1].uppercase() }
}

private fun ColumnSpec.SimpleColumn.toKotlinType(): TypeName {
  // TODO: Handle foreign keys that are now listed as normal columns
  val baseType =
    when (type) {
      "uuid" -> UUID::class.asTypeName()
      "varchar" -> String::class.asTypeName()
      "text" -> String::class.asTypeName()
      "timezone" -> TimeZone::class.asTypeName()
      "timestamptz" -> Instant::class.asTypeName()
      "interval" -> Duration::class.asTypeName()
      "monetary_amount" -> MonetaryAmount::class.asTypeName()
      "int4" -> Int::class.asTypeName()
      "int8" -> Long::class.asTypeName()
      "bool" -> Boolean::class.asTypeName()
      else -> error("Exposed generator doesn't know how to map column ot type '$type'")
    }
  return if (isNullable) {
    baseType.toNullable()
  } else {
    baseType
  }
}

private fun ColumnSpec.SimpleColumn.toInitializerBlock(): CodeBlock {
  return CodeBlock.builder {
    when (type) {
      "uuid" -> add("uuid(%S)", name)
      "timestamptz" -> add("%M(%S).default(%T.System.now())", timestampInitializer, name, Clock::class)
      "interval" -> add("duration(%S)", name)
      "monetary_amount" -> add("monetaryAmount(%S)", name)
      "int4" -> add("integer(%S)", name)
      "text" -> add("%L(%S)", type, name)
      "bool" -> add("%L(%S)", type, name)
      "timezone" -> add("timezone(%S)", name)
      else -> {
        add("%L(%S, %L)", type, name, size)
      }
    }
    if (isNullable) {
      add(".nullable()")
    }
  }
}

private fun ColumnSpec.EnumColumn.toInitializerBlock(): CodeBlock {
  return CodeBlock.of(
    """
    customEnumeration(
      name = %S,
      sql = %S,
      fromDb = { value -> %L.of(value as String) },
      toDb = { PgEnumValue(%S, it) },
    ) 
    """.trimIndent(),
    name,
    enumType,
    enumClass,
    enumType,
  )
}

// References the extension function Table.timestamp
private val timestampInitializer =
  MemberName("org.jetbrains.exposed.sql.kotlin.datetime", "timestamp")

// References the extension function Table.duration
private val intervalInitializer =
  MemberName("org.jetbrains.exposed.sql.kotlin.datetime", "duration")

internal fun String.toCamelCase(): String {
  val pattern = "_[a-z]".toRegex()
  return replace(pattern) { it.value.last().uppercase() }
}

private val intEntityIdType = EntityID::class.asClassName().parameterizedBy(Int::class.asTypeName())
private val columnType = Column::class.asClassName()
