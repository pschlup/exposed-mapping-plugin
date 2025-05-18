# Exposed Mapping Plugin

A Gradle plugin that generates Jetbrains Exposed ORM mapping for an existing PostgreSQL database at compile time.

## Features

- Automatically generates Kotlin data models from your PostgreSQL database schema
- Creates type-safe Exposed ORM mappings
- Supports PostgreSQL enum types
- Handles foreign key relationships
- Configurable output package and directory

## Installation

Add the plugin to your project's build script:

```kotlin
// build.gradle.kts
plugins {
    id("com.pschlup.exposedmapping.plugin") version "1.0.0"
}
```

## Usage

Configure the plugin in your build script:

```kotlin
// build.gradle.kts
exposedMapping {
    // Package name for generated models (default: "com.example.model")
    packageName = "com.myapp.model"

    // Output directory (default: "src/main/kotlin")
    outputDir = "src/main/kotlin"

    // Database schemas to process (default: ["public"])
    schemas = listOf("public", "custom_schema")

    // Database connection options (choose one approach)

    // Option 1: JDBC URL
    jdbcUrl = "jdbc:postgresql://localhost:5432/mydb"
    user = "postgres"
    password = "password"

    // Option 2: Individual properties
    serverName = "localhost"
    databaseName = "mydb"
    port = 5432
    user = "postgres"
    password = "password"
}
```

Then run the generation task:

```bash
./gradlew generateExposedMapping
```

## Configuration Options

| Option | Description | Default |
|--------|-------------|---------|
| `packageName` | Package name for generated models | `com.example.model` |
| `outputDir` | Directory where generated files will be placed | `src/main/kotlin` |
| `schemas` | List of database schemas to process | `["public"]` |
| `jdbcUrl` | JDBC URL for database connection | `null` |
| `serverName` | Database server hostname | `localhost` |
| `databaseName` | Database name | Required if not using jdbcUrl |
| `port` | Database port | `5432` |
| `user` | Database username | Required |
| `password` | Database password | Required |

## Environment Variables

The plugin will also check for the `DATABASE_URL` environment variable if no connection details are provided in the configuration.

## Generated Code

The plugin generates:

1. **Enum classes** for PostgreSQL enum types
2. **Model classes** for database tables with:
   - Properties for each column
   - Foreign key relationships
   - A nested Table object with column definitions
   - A BaseDao class with insert/update methods

Example generated model:

```kotlin
class UserModel(id: EntityID<Int>) : IntEntity(id) {
    var name by Table.name
    var email by Table.email
    var createdAt by Table.createdAt
    var role by Table.role

    // Foreign key relationship
    var department by DepartmentModel referencedOn Table.departmentId

    object Table : IntIdTable(name = "users") {
        val name = varchar("name", 255)
        val email = varchar("email", 255)
        val createdAt = timestamp("created_at")
        val role = customEnumeration(
            name = "role",
            sql = "user_role",
            fromDb = { value -> UserRole.of(value as String) },
            toDb = { PgEnumValue("user_role", it) },
        )
        val departmentId = reference("department_id", DepartmentModel.Table)
    }

    class BaseDao : IntEntityClass<UserModel>(Table, UserModel::class.java, { UserModel(it) }) {
        open fun insert(block: InsertStatement<Number>.() -> Unit) {
            Table.insert { block.invoke(it) }
        }

        open fun update(id: Int, block: UpdateStatement.() -> Unit) {
            Table.update({ Table.id eq id }) { block.invoke(it) }
        }
    }
}
```

## Publishing the Plugin

To publish this plugin to the Gradle Plugin Portal, follow these steps:

1. Create an account on the [Gradle Plugin Portal](https://plugins.gradle.org/)
2. Get your API key and secret from [your account settings](https://plugins.gradle.org/user/settings)
3. Add your credentials to your user's gradle.properties file (~/.gradle/gradle.properties):
   ```properties
   gradle.publish.key=your-key-here
   gradle.publish.secret=your-secret-here
   ```
4. Run the publish command:
   ```bash
   ./gradlew publishPlugins
   ```

The plugin will be published to the Gradle Plugin Portal and will be available for other developers to use.

## License

MIT License
