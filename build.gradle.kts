import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.4.10"
    id("net.fabricmc.fabric-loom-remap") version "1.17-SNAPSHOT"
    id("maven-publish")
}

val minecraftVersion: String = project.property("minecraft_version") as String
val yarnMappings: String = project.property("yarn_mappings") as String
val loaderVersion: String = project.property("loader_version") as String
val modVersion: String = project.property("mod_version") as String
val mavenGroup: String = project.property("maven_group") as String
val archivesBaseName: String = project.property("archives_base_name") as String
val fabricVersion: String = project.property("fabric_version") as String
val kotlinVersion: String = project.property("kotlin_version") as String
val fabricLanguageKotlinVersion: String = project.property("fabric_language_kotlin_version") as String

base {
    archivesName.set(archivesBaseName)
    group = mavenGroup
    version = modVersion
}

loom {
}

repositories {
    maven { name = "Fabric"; url = uri("https://maven.fabricmc.net/") }
    mavenCentral()
    maven { name = "Shedaniel"; url = uri("https://maven.shedaniel.me/") }
    maven { name = "TerraformersMC"; url = uri("https://maven.terraformersmc.com/") }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$yarnMappings:v2")
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricVersion")
    modImplementation("net.fabricmc:fabric-language-kotlin:$fabricLanguageKotlinVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:$kotlinVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")

    // Gson（JSON 解析，用于配置和 API 响应）
    modImplementation("com.google.code.gson:gson:2.11.0")

    // Cloth Config（GUI 设置界面）
    modImplementation("me.shedaniel.cloth:cloth-config-fabric:20.0.149")
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            artifactId = archivesBaseName
            from(components["java"])
        }
    }
}