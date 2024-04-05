plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    `java-gradle-plugin`
}

group = "net.derfruhling.minecraft"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("packmakerPlugin") {
            id = "net.derfruhling.packmaker"
            implementationClass = "net.derfruhling.minecraft.packmaker.PackmakerPlugin"
        }
    }
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
