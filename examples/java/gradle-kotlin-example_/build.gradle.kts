plugins {
    kotlin("jvm") version "1.9.23"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        url = uri("https://artifactory.openpreservation.org/artifactory/vera-dev")
    }
}

dependencies {
    implementation("io.github.opendataloader-project:opendataloader-pdf-core:0.0.10")
}

application {
    mainClass.set("org.example.MainKt")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions.jvmTarget = "11"
}
