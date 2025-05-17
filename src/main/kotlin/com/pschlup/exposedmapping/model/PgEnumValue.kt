package com.pschlup.exposedmapping.model

import org.postgresql.util.PGobject

interface DbEnum {
  val value: String
}

/** Identifies an enum value in Postgresql */
@Suppress("unused")
internal class PgEnumValue<T : DbEnum>(
  enumTypeName: String,
  enumValue: T?,
) : PGobject() {
  init {
    value = enumValue?.value
    type = enumTypeName
  }
}
