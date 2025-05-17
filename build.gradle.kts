plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    `maven-publish`
    kotlin("jvm") version "2.0.21"
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
    plugins {
        create("exposedMappingPlugin") {
            id = "com.exposed.mapping.plugin"
            implementationClass = "com.exposed.mapping.ExposedMappingPlugin"
        }
    }
}

publishing {
    repositories {
        maven {
            name = "localRepo"
            url = uri("${buildDir}/repo")
        }
    }
}
