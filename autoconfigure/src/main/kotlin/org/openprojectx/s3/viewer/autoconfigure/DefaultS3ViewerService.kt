package org.openprojectx.s3.viewer.autoconfigure

import org.openprojectx.s3.viewer.core.BucketBrowseResult
import org.openprojectx.s3.viewer.core.BucketObjectEntry
import org.openprojectx.s3.viewer.core.BucketObjectType
import org.openprojectx.s3.viewer.core.ObjectDownload
import org.openprojectx.s3.viewer.core.S3ViewerException
import org.openprojectx.s3.viewer.core.S3ViewerService
import org.openprojectx.s3.viewer.core.SearchResult
import org.openprojectx.s3.viewer.core.ViewerBucket
import org.openprojectx.s3.viewer.core.ViewerProvider
import software.amazon.nio.spi.s3.SpringAwareS3FileSystemProvider
import java.io.InputStream
import java.net.URI
import java.net.URLConnection
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name

internal open class DefaultS3ViewerService(
    private val properties: S3ViewerProperties
) : S3ViewerService {

    private val fileSystemProviders = ConcurrentHashMap<String, SpringAwareS3FileSystemProvider>()
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
                .map { entry -> toImmediateEntry(directory, normalizedPath, entry) }
                .distinct()
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

    override fun downloadObject(providerId: String, bucketName: String, key: String): ObjectDownload {
        val provider = getProvider(providerId)
        requireAllowedBucket(provider, bucketName)

        val fileSystem = fileSystem(provider, bucketName)
        val path = fileSystem.getPath("/${key.trimStart('/')}").normalize()

        if (!Files.exists(path) || Files.isDirectory(path)) {
            throw S3ViewerException("Object '$key' was not found in bucket '$bucketName'")
        }

        val attributes = Files.readAttributes(path, BasicFileAttributes::class.java)
        val fileName = path.name.ifBlank { key }
        val contentType = URLConnection.guessContentTypeFromName(fileName) ?: "application/octet-stream"

        return ObjectDownload(
            fileName = fileName,
            contentType = contentType,
            size = attributes.size(),
            inputStream = Files.newInputStream(path)
        )
    }

    open override fun uploadObject(
        providerId: String,
        bucketName: String,
        path: String?,
        fileName: String,
        inputStream: InputStream
    ): BucketObjectEntry {
        val provider = getProvider(providerId)
        requireAllowedBucket(provider, bucketName)

        val normalizedPath = normalizePath(path)
        val key = if (normalizedPath.isBlank()) fileName else "$normalizedPath/$fileName"

        val fileSystem = fileSystem(provider, bucketName)
        val targetPath = fileSystem.getPath("/${key.trimStart('/')}").normalize()

        val parentDir = targetPath.parent
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir)
        }

        Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING)

        val attributes = Files.readAttributes(targetPath, BasicFileAttributes::class.java)
        return BucketObjectEntry(
            name = fileName,
            key = key,
            type = BucketObjectType.FILE,
            size = attributes.size(),
            lastModified = attributes.lastModifiedTime()?.toInstant()?.takeIf { it != Instant.EPOCH }
        )
    }

    open override fun deleteObjects(providerId: String, bucketName: String, keys: List<String>) {
        val provider = getProvider(providerId)
        requireAllowedBucket(provider, bucketName)

        val fileSystem = fileSystem(provider, bucketName)
        val errors = mutableListOf<String>()

        for (key in keys) {
            val path = fileSystem.getPath("/${key.trimStart('/')}").normalize()
            try {
                if (Files.isDirectory(path)) {
                    deleteRecursively(path)
                } else {
                    Files.deleteIfExists(path)
                }
            } catch (ex: Exception) {
                errors.add("Failed to delete '$key': ${ex.message}")
            }
        }

        if (errors.isNotEmpty()) {
            throw S3ViewerException("Some objects could not be deleted: ${errors.joinToString("; ")}")
        }
    }

    override fun searchObjects(
        providerId: String,
        bucketName: String,
        query: String,
        path: String?,
        maxResults: Int
    ): SearchResult {
        val provider = getProvider(providerId)
        requireAllowedBucket(provider, bucketName)

        val normalizedPath = normalizePath(path)
        val rootDir = resolveDirectory(provider, bucketName, normalizedPath)
        val lowerQuery = query.lowercase()

        val entries = Files.walk(rootDir).use { stream ->
            stream
                .filter { !Files.isDirectory(it) || it != rootDir }
                .map { entry -> toEntry(rootDir, entry) }
                .filter { it.name.lowercase().contains(lowerQuery) }
                .sorted(compareBy<BucketObjectEntry> { it.type != BucketObjectType.DIRECTORY }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                .limit(maxResults.toLong())
                .toList()
        }

        return SearchResult(
            providerId = provider.id,
            bucketName = bucketName,
            query = query,
            entries = entries
        )
    }

    private fun deleteRecursively(path: Path) {
        Files.walk(path).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
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
        val rawPath = if (normalizedPath.isBlank()) "/" else "/$normalizedPath/"
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
                providerInstance(provider).getFileSystem(bucketUri)
            } catch (ex: Exception) {
                throw S3ViewerException(
                    "Failed to create an S3 file system for provider '${provider.id}' and bucket '$bucketName'",
                    ex
                )
            }
        }
    }

    private fun providerInstance(provider: S3ViewerProperties.Provider): SpringAwareS3FileSystemProvider =
        fileSystemProviders.computeIfAbsent(provider.id) {
            SpringAwareS3FileSystemProvider(provider)
        }

    private fun toEntry(baseDirectory: Path, entry: Path): BucketObjectEntry {
        val absoluteBaseDirectory = baseDirectory.toAbsolutePath().normalize()
        val absoluteEntry = entry.toAbsolutePath().normalize()

        val attributes = Files.readAttributes(absoluteEntry, BasicFileAttributes::class.java)
        val type = if (attributes.isDirectory) BucketObjectType.DIRECTORY else BucketObjectType.FILE
        val relative = absoluteBaseDirectory.relativize(absoluteEntry).invariantSeparatorsPathString.trim('/')
        val normalizedKey = listOf(absoluteBaseDirectory.invariantSeparatorsPathString.trim('/'), relative)
            .filter { it.isNotBlank() }
            .joinToString("/")
        val rawName = absoluteEntry.name.ifBlank { normalizedKey.ifBlank { "/" } }

        return BucketObjectEntry(
            name = if (type == BucketObjectType.DIRECTORY) rawName.trimEnd('/') else rawName,
            key = normalizedKey,
            type = type,
            size = if (attributes.isRegularFile) attributes.size() else null,
            lastModified = attributes.lastModifiedTime()?.toInstant()?.takeIf { it != Instant.EPOCH }
        )
    }

    private fun toImmediateEntry(baseDirectory: Path, normalizedPath: String, entry: Path): BucketObjectEntry {
        val absoluteBaseDirectory = baseDirectory.toAbsolutePath().normalize()
        val absoluteEntry = entry.toAbsolutePath().normalize()
        val relative = absoluteBaseDirectory.relativize(absoluteEntry).invariantSeparatorsPathString.trim('/')

        if ('/' in relative) {
            val directoryName = relative.substringBefore('/').trimEnd('/')
            val key = listOf(normalizedPath, directoryName)
                .filter { it.isNotBlank() }
                .joinToString("/")

            return BucketObjectEntry(
                name = directoryName,
                key = key,
                type = BucketObjectType.DIRECTORY
            )
        }

        return toEntry(baseDirectory, entry)
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
