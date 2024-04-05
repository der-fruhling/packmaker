package net.derfruhling.minecraft.packmaker

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodySubscribers

class ModrinthResolver {
    data class Handler(
        val short: (String) -> Unit,
        val embed: () -> Unit
    )

    private val handlers: MutableMap<String, Handler> = mutableMapOf()

    fun register(sha1Hash: String, handler: Handler) {
        handlers[sha1Hash] = handler
    }

    // intentionally slim
    @Serializable
    private data class VersionInfo(
        val id: String,
        val files: List<VersionFile>
    )

    // intentionally slim
    @Serializable
    private data class VersionFile(
        val url: String,
        val primary: Boolean,
        val hashes: VersionHashes
    )

    // intentionally slim
    @Serializable
    private data class VersionHashes(val sha1: String)

    @Serializable
    private data class GetVersionsFromHashes(
        val hashes: List<String>,
        val algorithm: String,
    )

    fun resolve() {
        val logger = LoggerFactory.getLogger(ModrinthResolver::class.java)
        val http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build()

        val json = Json {
            ignoreUnknownKeys = true
        }

        val hashes = GetVersionsFromHashes(
            hashes = handlers.keys.toList(),
            algorithm = "sha1"
        )

        logger.debug("Sending request for version files to Modrinth: {}", hashes)
        val request = http.send(HttpRequest.newBuilder(URI("https://api.modrinth.com/v2/version_files"))
            .POST(BodyPublishers.ofString(json.encodeToString(hashes)))
            .header("User-Agent", "packmaker")
            .header("Content-Type", "application/json")
            .build()) { BodySubscribers.ofString(Charsets.UTF_8) }

        val response: Map<String, VersionInfo> = json.decodeFromString(request.body())

        for ((hash, info) in response) {
            info.files.find { it.hashes.sha1 == hash }?.url?.let { url ->
                logger.debug("FOUND {} -> {} at {}", hash, info.id, url)
                handlers.remove(hash)?.short?.invoke(url)
            }
        }

        for ((hash, handler) in handlers) {
            logger.debug("NOT FOUND {}", hash)
            handler.embed()
        }
    }
}