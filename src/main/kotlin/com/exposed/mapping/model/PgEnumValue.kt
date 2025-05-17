package com.exposed.mapping.model

import org.postgresql.util.PGobject

interface DbEnum {
  val value: String
}

/** Identifies an enum value in Postgresql */
internal class PgEnumValue<T : DbEnum>(
  enumTypeName: String,
  enumValue: T?,
) : PGobject() {
  init {
    value = enumValue?.value
    type = enumTypeName
  }
}
