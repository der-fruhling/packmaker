package net.derfruhling.minecraft.packmaker

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Zip
import java.net.URI

class PackmakerPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension = target.extensions.create("packageMetadata", PackageExtension::class.java)

        val minecraftConfiguration = target.configurations.register("minecraft") {
            it.isCanBeResolved = false
            it.isCanBeConsumed = false
        }

        val commonModJarConfiguration = target.configurations.register("mod")
        val clientModJarConfiguration = target.configurations.register("modClient")
        val serverModJarConfiguration = target.configurations.register("modDedicatedServer")

        val commonOptionalModJarConfiguration = target.configurations.register("optionalMod")
        val clientOptionalModJarConfiguration = target.configurations.register("optionalModClient")
        val serverOptionalModJarConfiguration = target.configurations.register("optionalModDedicatedServer")

        val resourcePackConfiguration = target.configurations.register("resourcePack", ::configureZipResolutionStrategy)
        val dataPackConfiguration = target.configurations.register("dataPack", ::configureZipResolutionStrategy)

        target.repositories.exclusiveContent {
            it.forRepository {
                target.repositories.maven { repo ->
                    repo.name = "Modrinth"
                    repo.url = URI("https://api.modrinth.com/maven")
                }
            }

            it.filter { filter ->
                filter.includeGroup("maven.modrinth")
            }
        }

        target.tasks.register("createDirectoryStructure") {
            it.outputs.dirs("src", "src/client-overrides", "src/overrides", "src/server-overrides")

            it.doFirst { task ->
                task.outputs.files.forEach { file -> file.mkdirs() }
            }
        }

        target.tasks.register("packageModrinth", PackageTask::class.java) {
            it.dependsOn("createDirectoryStructure")

            it.extension.set(extension)
            it.outputFile.set(target.layout.buildDirectory.file("${extension.name.get()} ${extension.version.get()}.mrpack"))
            it.overridesDirectory.set(target.projectDir.resolve("src/overrides"))
            it.clientOverridesDirectory.set(target.projectDir.resolve("src/client-overrides"))
            it.serverOverridesDirectory.set(target.projectDir.resolve("src/server-overrides"))
            it.minecraftConfiguration.set(minecraftConfiguration.get().dependencies.associate { dep -> Pair(dep.name, dep.version!!) })
            it.commonMods.set(commonModJarConfiguration)
            it.clientMods.set(clientModJarConfiguration)
            it.serverMods.set(serverModJarConfiguration)
            it.commonOptionalMods.set(commonOptionalModJarConfiguration)
            it.clientOptionalMods.set(clientOptionalModJarConfiguration)
            it.serverOptionalMods.set(serverOptionalModJarConfiguration)
            it.clientResourcePacks.set(resourcePackConfiguration)
            it.serverDataPacks.set(dataPackConfiguration)
            it.worldName.set("world")
        }

        target.tasks.register("distServer", Copy::class.java) { task ->
            task.dependsOn("createDirectoryStructure")

            task.into(target.layout.buildDirectory.file("distServer"))

            task.from(commonModJarConfiguration) { it.into("mods") }
            task.from(serverModJarConfiguration) { it.into("mods") }
            task.from(commonOptionalModJarConfiguration) { it.into("mods") }
            task.from(serverOptionalModJarConfiguration) { it.into("mods") }
            task.from(dataPackConfiguration) { it.into("world/datapacks") }

            task.from(target.projectDir.resolve("src/overrides")) { it.into("/") }
            task.from(target.projectDir.resolve("src/server-overrides")) { it.into("/") }
        }

        target.tasks.maybeCreate("build").dependsOn("packageModrinth")
    }

    private fun configureZipResolutionStrategy(it: Configuration) {
        it.resolutionStrategy.eachDependency { resolve ->
            resolve.artifactSelection { sel ->
                sel.selectArtifact("zip", null, null)
            }
        }
    }
}