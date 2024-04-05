package net.derfruhling.minecraft.packmaker

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@CacheableTask
abstract class PackageTask : DefaultTask() {
    @get:Input abstract val extension: Property<PackageExtension>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val overridesDirectory: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val clientOverridesDirectory: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val serverOverridesDirectory: RegularFileProperty

    @get:OutputFile abstract val outputFile: RegularFileProperty
    @get:Input abstract val minecraftConfiguration: MapProperty<String, String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val commonMods: Property<Configuration>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val clientMods: Property<Configuration>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val serverMods: Property<Configuration>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val commonOptionalMods: Property<Configuration>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val clientOptionalMods: Property<Configuration>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val serverOptionalMods: Property<Configuration>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val clientResourcePacks: Property<Configuration>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val serverDataPacks: Property<Configuration>

    @get:Input abstract val worldName: Property<String>

    @OptIn(ExperimentalSerializationApi::class, ExperimentalStdlibApi::class)
    @TaskAction
    fun runPackage() {
        val logger = LoggerFactory.getLogger(PackageTask::class.java);
        ZipOutputStream(FileOutputStream(outputFile.get().asFile)).use { os ->
            os.setLevel(4)

            val extension = extension.get()

            val files = mutableListOf<PackagedFile>()
            val resolver = ModrinthResolver()

            fun registerResolve(config: Property<Configuration>, env: Env, path: String) {
                logger.debug("This config's files: {}", config.get().resolve())
                for(dependency in config.get().resolvedConfiguration.firstLevelModuleDependencies) {
                    val depFiles = config.get().resolvedConfiguration.getFiles {
                        logger.debug("Reviewing dependency: {}", it)
                        it.name == dependency.moduleName &&
                        it.version == dependency.moduleVersion
                    }
                    val jar = depFiles.find { it.extension == "jar" }
                    LoggerFactory.getLogger(PackageTask::class.java).debug("Dependency {}'s files: {}", dependency.module, depFiles)
                    if(jar == null) {
                        throw IllegalStateException("Dependency ${dependency.module} did not return a JAR!")
                    }

                    val bytes = jar.readBytes()
                    val sha1 = MessageDigest.getInstance("SHA-1").digest(bytes).toHexString()

                    logger.debug("File {} has SHA1 hash {}", jar.absolutePath, sha1)
                    resolver.register(sha1, ModrinthResolver.Handler(
                        short = { url ->
                            logger.info("Added ${dependency.moduleName} version ${dependency.moduleVersion}")
                            files.add(PackagedFile(
                                path = "$path/${dependency.moduleName}-${dependency.moduleVersion}.${jar.extension}",
                                hashes = Hashes(bytes),
                                downloads = listOf(url),
                                env = env,
                                fileSize = jar.length()
                            ))
                        },
                        embed = {
                            logger.info("EMBEDDING ${dependency.moduleName} version ${dependency.moduleVersion}")
                            os.putNextEntry(ZipEntry("${env.overridesDir}/$path/${dependency.moduleName}-${dependency.moduleVersion}.${jar.extension}"))
                            os.write(bytes)
                        }
                    ))
                }
            }

            registerResolve(commonMods, Env(EnvSupport.Required, EnvSupport.Required), "mods")
            registerResolve(clientMods, Env(EnvSupport.Required, EnvSupport.Unsupported), "mods")
            registerResolve(serverMods, Env(EnvSupport.Unsupported, EnvSupport.Required), "mods")
            registerResolve(commonOptionalMods, Env(EnvSupport.Optional, EnvSupport.Optional), "mods")
            registerResolve(clientOptionalMods, Env(EnvSupport.Optional, EnvSupport.Unsupported), "mods")
            registerResolve(serverOptionalMods, Env(EnvSupport.Unsupported, EnvSupport.Optional), "mods")
            registerResolve(clientResourcePacks, Env(EnvSupport.Optional, EnvSupport.Unsupported), "resourcepacks")
            registerResolve(serverDataPacks, Env(EnvSupport.Unsupported, EnvSupport.Required), "${worldName.get()}/datapacks")

            resolver.resolve()

            os.putNextEntry(ZipEntry("modrinth.index.json"))
            Json.encodeToStream(PackageMetadata(
                versionId = extension.version.get(),
                name = extension.name.get(),
                summary = extension.summary.get(),
                files = files.toList(),
                dependencies = minecraftConfiguration.get()
            ), os)
        }
    }
}