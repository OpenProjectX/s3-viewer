package org.openprojectx.s3.viewer.autoconfigure

import org.openprojectx.s3.viewer.core.BucketBrowseResult
import org.openprojectx.s3.viewer.core.BucketObjectEntry
import org.openprojectx.s3.viewer.core.BucketObjectType
import org.openprojectx.s3.viewer.core.S3ViewerException
import org.openprojectx.s3.viewer.core.S3ViewerService
import org.openprojectx.s3.viewer.core.ViewerBucket
import org.openprojectx.s3.viewer.core.ViewerProvider
import software.amazon.nio.spi.s3.S3FileSystemProvider
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.FileSystemAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name

internal class DefaultS3ViewerService(
    private val properties: S3ViewerProperties
) : S3ViewerService {

    private val fileSystemProvider = S3FileSystemProvider()
    private val fileSystems = ConcurrentHashMap<String, FileSystem>()

    override fun listProviders(): List<ViewerProvider> = properties.providers.map { provider ->
        ViewerProvider(
            id = provider.id,
            name = provider.name,
            endpoint = provider.endpoint,
            region = provider.region,
            bucketCount = provider.buckets.size,
            pathStyleAccess = provider.pathStyleAccess
        )
    }

    override fun listBuckets(providerId: String): List<ViewerBucket> {
        val provider = getProvider(providerId)
        return provider.buckets.map { bucket ->
            ViewerBucket(
                providerId = provider.id,
                name = bucket,
                configured = true
            )
        }
    }

    override fun browseBucket(providerId: String, bucketName: String, path: String?): BucketBrowseResult {
        val provider = getProvider(providerId)
        requireAllowedBucket(provider, bucketName)

        val normalizedPath = normalizePath(path)
        val directory = resolveDirectory(provider, bucketName, normalizedPath)
        val entries = Files.list(directory).use { stream ->
            stream
                .map { entry -> toEntry(directory, entry) }
                .sorted(compareBy<BucketObjectEntry> { it.type != BucketObjectType.DIRECTORY }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                .toList()
        }

        return BucketBrowseResult(
            providerId = provider.id,
            bucketName = bucketName,
            path = normalizedPath,
            parentPath = parentPath(normalizedPath),
            entries = entries
        )
    }

    private fun getProvider(providerId: String): S3ViewerProperties.Provider =
        properties.providers.firstOrNull { it.id == providerId }
            ?: throw S3ViewerException("Provider '$providerId' is not configured")

    private fun requireAllowedBucket(provider: S3ViewerProperties.Provider, bucketName: String) {
        if (provider.buckets.isNotEmpty() && bucketName !in provider.buckets) {
            throw S3ViewerException("Bucket '$bucketName' is not configured for provider '${provider.id}'")
        }
    }

    private fun resolveDirectory(
        provider: S3ViewerProperties.Provider,
        bucketName: String,
        normalizedPath: String
    ): Path {
        val fileSystem = fileSystem(provider, bucketName)
        val rawPath = if (normalizedPath.isBlank()) "/" else "/$normalizedPath"
        val path = fileSystem.getPath(rawPath).normalize()
        if (!Files.exists(path)) {
            throw S3ViewerException("Path '$normalizedPath' was not found in bucket '$bucketName'")
        }
        return path
    }

    private fun fileSystem(provider: S3ViewerProperties.Provider, bucketName: String): FileSystem {
        val key = "${provider.id}::$bucketName"
        return fileSystems.computeIfAbsent(key) {
            val bucketUri = URI.create("s3://$bucketName")
            try {
                fileSystemProvider.newFileSystem(bucketUri, createEnvironment(provider))
            } catch (_: FileSystemAlreadyExistsException) {
                fileSystemProvider.getFileSystem(bucketUri)
            } catch (ex: Exception) {
                throw S3ViewerException(
                    "Failed to create an S3 file system for provider '${provider.id}' and bucket '$bucketName'",
                    ex
                )
            }
        }
    }

    private fun createEnvironment(provider: S3ViewerProperties.Provider): Map<String, *> =
        linkedMapOf<String, Any>(
            "region" to provider.region,
            "endpoint" to provider.endpoint,
            "accessKey" to provider.accessKey,
            "secretAccessKey" to provider.secretKey,
            "pathStyleAccess" to provider.pathStyleAccess
        )

    private fun toEntry(baseDirectory: Path, entry: Path): BucketObjectEntry {
        val attributes = Files.readAttributes(entry, BasicFileAttributes::class.java)
        val type = if (attributes.isDirectory) BucketObjectType.DIRECTORY else BucketObjectType.FILE
        val relative = baseDirectory.relativize(entry).invariantSeparatorsPathString.trim('/')
        val normalizedKey = listOf(baseDirectory.invariantSeparatorsPathString.trim('/'), relative)
            .filter { it.isNotBlank() }
            .joinToString("/")

        return BucketObjectEntry(
            name = entry.name.ifBlank { normalizedKey.ifBlank { "/" } },
            key = normalizedKey,
            type = type,
            size = if (attributes.isRegularFile) attributes.size() else null,
            lastModified = attributes.lastModifiedTime()?.toInstant()?.takeIf { it != Instant.EPOCH }
        )
    }

    private fun normalizePath(path: String?): String =
        path
            ?.trim()
            ?.trim('/')
            .orEmpty()

    private fun parentPath(path: String): String? {
        if (path.isBlank()) {
            return null
        }
        return path.substringBeforeLast('/', "")
            .ifBlank { null }
    }
}
