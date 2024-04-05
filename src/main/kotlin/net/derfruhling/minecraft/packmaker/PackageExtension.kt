package net.derfruhling.minecraft.packmaker

import org.gradle.api.provider.Property

interface PackageExtension {
    val version: Property<String>
    val name: Property<String>
    val summary: Property<String>
}
