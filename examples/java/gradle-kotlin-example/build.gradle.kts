import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.23"
    application
}

application {
    mainClass.set("org.example.gradlekt.MainKt")
}

group = "org.example.gradlekt"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://artifactory.openpreservation.org/artifactory/vera-dev")
}

dependencies {
    implementation("org.opendataloader:opendataloader-pdf-core:1.0.0")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
