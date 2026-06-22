package org.openprojectx.s3.viewer.autoconfigure

import org.openprojectx.s3.viewer.core.BucketBrowseResult
import org.openprojectx.s3.viewer.core.BucketObjectEntry
import org.openprojectx.s3.viewer.core.BucketObjectType
import org.openprojectx.s3.viewer.core.ObjectDownload
import org.openprojectx.s3.viewer.core.ParquetSchemaPreview
import org.openprojectx.s3.viewer.core.S3ViewerException
import org.openprojectx.s3.viewer.core.S3ViewerService
import org.openprojectx.s3.viewer.core.SearchResult
import org.openprojectx.s3.viewer.core.TextObjectPreview
import org.openprojectx.s3.viewer.core.ViewerBucket
import org.openprojectx.s3.viewer.core.ViewerProvider
import org.apache.parquet.hadoop.ParquetFileReader
import org.apache.parquet.io.InputFile
import org.apache.parquet.io.SeekableInputStream
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.nio.spi.s3.SpringAwareS3FileSystemProvider
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.net.URI
import java.net.URLConnection
import java.nio.ByteBuffer
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.StandardOpenOption
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
        val path = resolveObjectPath(provider, bucketName, key)
        val attributes = Files.readAttributes(path, BasicFileAttributes::class.java)
        val fileName = path.name.ifBlank { key }
        val contentType = detectContentType(provider, bucketName, key, path, fileName)

        return ObjectDownload(
            fileName = fileName,
            contentType = contentType,
            size = attributes.size(),
            inputStream = Files.newInputStream(path)
        )
    }

    override fun previewTextObject(providerId: String, bucketName: String, key: String, maxBytes: Long): TextObjectPreview {
        val provider = getProvider(providerId)
        requireAllowedBucket(provider, bucketName)
        val path = resolveObjectPath(provider, bucketName, key)
        val attributes = Files.readAttributes(path, BasicFileAttributes::class.java)
        val fileName = path.name.ifBlank { key }
        val contentType = detectContentType(provider, bucketName, key, path, fileName)

        if (!isTextPreviewSupported(fileName, contentType)) {
            throw S3ViewerException("Object '$key' is not a supported text preview type")
        }

        val previewLimit = maxBytes.coerceIn(1, MAX_TEXT_PREVIEW_BYTES)
        val buffer = ByteArrayOutputStream()
        Files.newInputStream(path).use { input ->
            val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
            var remaining = previewLimit
            while (remaining > 0) {
                val read = input.read(chunk, 0, minOf(chunk.size.toLong(), remaining).toInt())
                if (read < 0) {
                    break
                }
                buffer.write(chunk, 0, read)
                remaining -= read
            }
        }

        return TextObjectPreview(
            key = key,
            fileName = fileName,
            contentType = contentType,
            size = attributes.size(),
            truncated = attributes.size() > previewLimit,
            content = buffer.toByteArray().toString(Charsets.UTF_8)
        )
    }

    override fun previewParquetSchema(providerId: String, bucketName: String, key: String): ParquetSchemaPreview {
        val provider = getProvider(providerId)
        requireAllowedBucket(provider, bucketName)
        val path = resolveObjectPath(provider, bucketName, key)
        val attributes = Files.readAttributes(path, BasicFileAttributes::class.java)
        val fileName = path.name.ifBlank { key }
        val contentType = detectContentType(provider, bucketName, key, path, fileName)

        if (!isParquetPreviewSupported(fileName, contentType)) {
            throw S3ViewerException("Object '$key' is not a supported parquet preview type")
        }

        try {
            val inputFile = SeekablePathInputFile(path, attributes.size())
            val schema = ParquetFileReader.open(inputFile).use { reader ->
                reader.footer.fileMetaData.schema.toString()
            }
            return ParquetSchemaPreview(
                key = key,
                fileName = fileName,
                size = attributes.size(),
                schema = schema
            )
        } catch (ex: Exception) {
            throw S3ViewerException("Failed to read parquet schema for object '$key'", ex)
        }
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

    private fun resolveObjectPath(provider: S3ViewerProperties.Provider, bucketName: String, key: String): Path {
        val fileSystem = fileSystem(provider, bucketName)
        val path = fileSystem.getPath("/${key.trimStart('/')}").normalize()

        if (!Files.exists(path) || Files.isDirectory(path)) {
            throw S3ViewerException("Object '$key' was not found in bucket '$bucketName'")
        }

        return path
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

    private fun detectContentType(
        provider: S3ViewerProperties.Provider,
        bucketName: String,
        key: String,
        path: Path,
        fileName: String
    ): String =
        s3MetadataContentType(provider, bucketName, key)
            ?: Files.probeContentType(path)
            ?: URLConnection.guessContentTypeFromName(fileName)
            ?: "application/octet-stream"

    private fun s3MetadataContentType(
        provider: S3ViewerProperties.Provider,
        bucketName: String,
        key: String
    ): String? =
        try {
            S3Client.builder()
                .region(Region.of(provider.region))
                .endpointOverride(URI.create(provider.endpoint))
                .forcePathStyle(provider.pathStyleAccess)
                .credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(provider.accessKey, provider.secretKey)
                    )
                )
                .build()
                .use { s3 ->
                    s3.headObject(
                        HeadObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .build()
                    ).contentType()?.takeIf { it.isNotBlank() }
                }
        } catch (_: Exception) {
            null
        }

    private fun isTextPreviewSupported(fileName: String, contentType: String): Boolean {
        val lowerName = fileName.lowercase()
        val lowerContentType = contentType.lowercase()
        return lowerContentType.startsWith("text/") ||
                lowerContentType in TEXT_PREVIEW_CONTENT_TYPES ||
                lowerName.endsWith(".txt") ||
                lowerName.endsWith(".json")
    }

    private fun isParquetPreviewSupported(fileName: String, contentType: String): Boolean {
        val lowerName = fileName.lowercase()
        val lowerContentType = contentType.lowercase()
        return lowerName.endsWith(".parquet") || lowerContentType in PARQUET_CONTENT_TYPES
    }

    companion object {
        private const val MAX_TEXT_PREVIEW_BYTES = 1024 * 1024L

        private val TEXT_PREVIEW_CONTENT_TYPES = setOf(
            "application/json",
            "application/x-ndjson",
            "application/jsonlines",
            "application/xml",
            "application/yaml",
            "application/x-yaml"
        )

        private val PARQUET_CONTENT_TYPES = setOf(
            "application/vnd.apache.parquet",
            "application/x-parquet"
        )
    }
}

private class SeekablePathInputFile(
    private val path: Path,
    private val length: Long
) : InputFile {
    override fun getLength(): Long = length

    override fun newStream(): SeekableInputStream =
        SeekablePathInputStream(path)
}

private class SeekablePathInputStream(path: Path) : SeekableInputStream() {
    private val channel = Files.newByteChannel(path, StandardOpenOption.READ)

    override fun getPos(): Long = channel.position()

    override fun seek(newPos: Long) {
        channel.position(newPos)
    }

    override fun read(): Int {
        val buffer = ByteBuffer.allocate(1)
        val read = channel.read(buffer)
        if (read < 0) {
            return -1
        }
        buffer.flip()
        return buffer.get().toInt() and 0xff
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
        channel.read(ByteBuffer.wrap(bytes, offset, length))

    override fun read(buffer: ByteBuffer): Int =
        channel.read(buffer)

    override fun readFully(bytes: ByteArray) {
        readFully(bytes, 0, bytes.size)
    }

    override fun readFully(bytes: ByteArray, start: Int, len: Int) {
        val buffer = ByteBuffer.wrap(bytes, start, len)
        readFully(buffer)
    }

    override fun readFully(buffer: ByteBuffer) {
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                throw EOFException("Reached end of file before filling buffer")
            }
        }
    }

    override fun close() {
        channel.close()
    }
}
