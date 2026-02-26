import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("fabric-loom")
    id("org.jetbrains.kotlin.jvm")
    `java-library`
    `maven-publish`
}

group = "com.packetmine"
version = "1.0.0"

val minecraftVersion: String by project
val yarnMappings: String by project
val loaderVersion: String by project
val meteorVersion: String by project

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.meteordev.org/releases")
    maven("https://maven.meteordev.org/snapshots")
}

dependencies {
    // Minecraft
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$yarnMappings:v2")
    
    // Fabric Loader
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    
    // Meteor Client
    modImplementation("meteordevelopment:meteor-client:$meteorVersion")
    
    // Kotlin 标准库
    implementation(kotlin("stdlib"))
}

tasks {
    withType<JavaCompile> {
        options.release = 21
    }
    
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "21"
            freeCompilerArgs = listOf("-Xjvm-default=all")
        }
    }
    
    processResources {
        inputs.property("version", project.version)
        filesMatching("fabric.mod.json") {
            expand("version" to project.version)
        }
    }
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
