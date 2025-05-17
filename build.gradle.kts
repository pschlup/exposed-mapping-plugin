plugins {
  `java-gradle-plugin`
  `kotlin-dsl`
  `maven-publish`
  kotlin("jvm") version "2.0.21"
  id("com.gradle.plugin-publish") version "1.3.1"
}

repositories {
  mavenCentral()
  gradlePluginPortal()
}

dependencies {
  implementation(kotlin("gradle-plugin", "2.0.21"))
  implementation(kotlin("stdlib-jdk8"))

  // Database
  implementation("org.postgresql:postgresql:42.7.3")

  // Exposed
  implementation("org.jetbrains.exposed:exposed-core:0.47.0")
  implementation("org.jetbrains.exposed:exposed-kotlin-datetime:0.47.0")
  implementation("org.jetbrains.exposed:exposed-money:0.47.0")
  implementation("org.jetbrains.exposed:exposed-dao:0.47.0")

  // Code generation
  implementation("com.squareup:kotlinpoet:1.16.0")

  // Date/Time
  implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
}

gradlePlugin {
  website = "https://github.com/pschlup/exposed-mapping-plugin"
  vcsUrl = "https://github.com/pschlup/exposed-mapping-plugin.git"

  plugins {
    create("exposedMappingPlugin") {
      id = "com.pschlup.exposedmapping.plugin"
      implementationClass = "com.pschlup.exposedmapping.ExposedMappingPlugin"
      displayName = "Exposed Mapping Plugin"
      description =
        "A Gradle plugin that generates Jetbrains Exposed ORM mapping for an existing PostgreSQL database at" +
        " compile time"
      tags.set(listOf("exposed", "orm", "postgresql", "database", "code-generation", "kotlin"))
    }
  }
}

publishing {
  repositories {
    // Publishes to ~/.m2/repository
    mavenLocal()
  }

  publications {
    create<MavenPublication>("maven") {
      groupId = "com.pschlup"
      artifactId = "exposedmapping.plugin"
      version = "1.0.0"

      from(components["java"])
    }
  }
}
