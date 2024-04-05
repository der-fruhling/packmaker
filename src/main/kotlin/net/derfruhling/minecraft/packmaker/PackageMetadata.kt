package net.derfruhling.minecraft.packmaker

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path
import java.security.MessageDigest

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class PackageMetadata(
    val versionId: String,
    val name: String,
    val summary: String,
    val files: List<PackagedFile>,
    val dependencies: Map<String, String>,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val formatVersion: Int = 1,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val game: String = "minecraft"
)

@Serializable
data class PackagedFile(
    val path: String,
    val hashes: Hashes,
    val env: Env? = null,
    val downloads: List<String>,
    val fileSize: Long
)

@Serializable
data class Hashes(
    val sha1: String,
    val sha256: String,
    val sha512: String,
    val md5: String
) {
    companion object {
        private val SHA1 = MessageDigest.getInstance("SHA-1")
        private val SHA256 = MessageDigest.getInstance("SHA-256")
        private val SHA512 = MessageDigest.getInstance("SHA-512")
        private val MD5 = MessageDigest.getInstance("MD5")
    }

    @OptIn(ExperimentalStdlibApi::class)
    constructor(data: ByteArray) : this(
        sha1 = SHA1.digest(data).toHexString(),
        sha256 = SHA256.digest(data).toHexString(),
        sha512 = SHA512.digest(data).toHexString(),
        md5 = MD5.digest(data).toHexString(),
    )
}

@Serializable
data class Env(
    val client: EnvSupport,
    val server: EnvSupport
) {
    val isCommon get() = client != EnvSupport.Unsupported && server != EnvSupport.Unsupported
    val isServerOnly get() = client == EnvSupport.Unsupported && server != EnvSupport.Unsupported
    val isClientOnly get() = client != EnvSupport.Unsupported && server == EnvSupport.Unsupported

    val overridesDir get() = when {
        isCommon -> "overrides"
        isServerOnly -> "server-overrides"
        isClientOnly -> "client-overrides"
        else -> "overrides"
    }
}

@Serializable
enum class EnvSupport {
    @SerialName("required") Required,
    @SerialName("optional") Optional,
    @SerialName("unsupported") Unsupported
}
