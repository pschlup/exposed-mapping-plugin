package com.pschlup.exposedmapping

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import kotlin.reflect.KClass

internal fun TypeName.toNullable() =
  copy(nullable = true)

internal fun FileSpec.Companion.builder(
  className: ClassName,
  block: FileSpec.Builder.() -> Unit,
): FileSpec {
  return builder(className)
    .apply {
      addFileComment(
        """
        **************************************************************************************
        **************************************************************************************
          DO NOT MODIFY: Auto-generated model implementation based on the database structure
        **************************************************************************************
        **************************************************************************************
        """.trimIndent(),
      )
      block()
    }.build()
}

internal fun TypeSpec.Companion.classBuilder(
  name: String,
  block: TypeSpec.Builder.() -> Unit,
): TypeSpec {
  return classBuilder(name)
    .apply {
      block()
    }.build()
}

internal fun TypeSpec.Companion.classBuilder(
  name: ClassName,
  block: TypeSpec.Builder.() -> Unit,
): TypeSpec {
  return classBuilder(name)
    .apply {
      block()
    }.build()
}

internal fun TypeSpec.Companion.enumBuilder(
  name: ClassName,
  block: TypeSpec.Builder.() -> Unit,
): TypeSpec {
  return enumBuilder(name)
    .apply {
      block()
    }.build()
}

internal fun TypeSpec.Companion.objectBuilder(
  name: String,
  block: TypeSpec.Builder.() -> Unit,
): TypeSpec {
  return objectBuilder(name)
    .apply {
      block()
    }.build()
}

internal fun TypeSpec.Companion.companionObjectBuilder(block: TypeSpec.Builder.() -> Unit): TypeSpec {
  return companionObjectBuilder()
    .apply {
      block()
    }.build()
}

internal fun TypeSpec.Companion.anonymousClassBuilder(block: TypeSpec.Builder.() -> Unit): TypeSpec {
  return anonymousClassBuilder()
    .apply {
      block()
    }.build()
}

internal fun FunSpec.Companion.builder(
  name: String,
  block: FunSpec.Builder.() -> Unit,
): FunSpec {
  return builder(name)
    .apply {
      block()
    }.build()
}

internal fun FunSpec.Companion.constructorBuilder(block: FunSpec.Builder.() -> Unit): FunSpec {
  return constructorBuilder()
    .apply {
      block()
    }.build()
}

internal fun CodeBlock.Companion.builder(block: CodeBlock.Builder.() -> Unit): CodeBlock {
  return CodeBlock
    .Builder()
    .apply {
      block()
    }.build()
}

internal fun PropertySpec.Companion.builder(
  name: String,
  type: TypeName,
  vararg modifiers: KModifier,
  block: PropertySpec.Builder.() -> Unit,
): PropertySpec {
  return builder(name = name, type = type, modifiers = modifiers)
    .apply {
      block()
    }.build()
}

internal fun PropertySpec.Companion.builder(
  name: String,
  type: KClass<*>,
  vararg modifiers: KModifier,
  block: PropertySpec.Builder.() -> Unit,
): PropertySpec {
  return builder(name = name, type = type, modifiers = modifiers)
    .apply {
      block()
    }.build()
}
