package org.openprojectx.s3.viewer.autoconfigure

import org.openprojectx.s3.viewer.core.BucketBrowseResult
import org.openprojectx.s3.viewer.core.BucketObjectEntry
import org.openprojectx.s3.viewer.core.BucketObjectType
import org.openprojectx.s3.viewer.core.AvroDataPreview
import org.openprojectx.s3.viewer.core.AvroSchemaPreview
import org.openprojectx.s3.viewer.core.ObjectDownload
import org.openprojectx.s3.viewer.core.ParquetSchemaPreview
import org.openprojectx.s3.viewer.core.S3ViewerException
import org.openprojectx.s3.viewer.core.S3ViewerService
import org.openprojectx.s3.viewer.core.SearchResult
import org.openprojectx.s3.viewer.core.TextObjectPreview
import org.openprojectx.s3.viewer.core.ViewerBucket
import org.openprojectx.s3.viewer.core.ViewerProvider
import org.apache.avro.Schema as AvroSchema
import org.apache.avro.file.DataFileReader
import org.apache.avro.file.SeekableInput
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericRecord
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.io.EncoderFactory
import org.apache.parquet.hadoop.ParquetFileReader
import org.apache.parquet.io.InputFile
import org.apache.parquet.io.SeekableInputStream
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.http.apache.ProxyConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.model.S3Object
import software.amazon.nio.spi.s3.SpringAwareS3FileSystemProvider
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.net.URI
import java.net.URLConnection
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
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
        val prefix = directoryPrefix(normalizedPath)
        val entries = mutableListOf<BucketObjectEntry>()
        var hasDirectoryMarker = normalizedPath.isBlank()

        s3Client(provider).use { s3 ->
            val response = try {
                s3.listObjectsV2(
                    ListObjectsV2Request.builder()
                        .bucket(bucketName)
                        .prefix(prefix)
                        .delimiter("/")
                        .build()
                )
            } catch (ex: S3Exception) {
                if (ex.statusCode() == 404 && normalizedPath.isNotBlank()) {
                    if (s3.directoryExists(bucketName, normalizedPath)) {
                        hasDirectoryMarker = true
                        null
                    } else {
                        throw S3ViewerException("Path '$normalizedPath' was not found in bucket '$bucketName'", ex)
                    }
                } else {
                    throw ex
                }
            }

            response?.commonPrefixes()?.forEach { commonPrefix ->
                val key = commonPrefix.prefix().trimEnd('/')
                val name = key.removePrefix(prefix).trim('/').substringBefore('/')
                if (name.isNotBlank()) {
                    entries.add(
                        BucketObjectEntry(
                            name = name,
                            key = key,
                            type = BucketObjectType.DIRECTORY
                        )
                    )
                }
            }

            response?.contents()?.forEach { s3Object ->
                val key = s3Object.key()
                when {
                    key == prefix -> hasDirectoryMarker = true
                    key.endsWith("/") -> {
                        val directoryKey = key.trimEnd('/')
                        val name = directoryKey.removePrefix(prefix).trim('/').substringBefore('/')
                        if (name.isNotBlank()) {
                            entries.add(
                                BucketObjectEntry(
                                    name = name,
                                    key = directoryKey,
                                    type = BucketObjectType.DIRECTORY,
                                    lastModified = s3Object.lastModified()
                                )
                            )
                        }
                    }
                    else -> {
                        val name = key.removePrefix(prefix)
                        if (name.isNotBlank() && '/' !in name) {
                            entries.add(s3Object.toFileEntry(key, name))
                        }
                    }
                }
            }
        }

        val sortedEntries = entries
            .distinctBy { it.key }
            .sortedWith(compareBy<BucketObjectEntry> { it.type != BucketObjectType.DIRECTORY }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        if (sortedEntries.isEmpty() && !hasDirectoryMarker && normalizedPath.isNotBlank()) {
            throw S3ViewerException("Path '$normalizedPath' was not found in bucket '$bucketName'")
        }

        return BucketBrowseResult(
            providerId = provider.id,
            bucketName = bucketName,
            path = normalizedPath,
            parentPath = parentPath(normalizedPath),
            entries = sortedEntries
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

    override fun previewAvroSchema(providerId: String, bucketName: String, key: String): AvroSchemaPreview {
        val provider = getProvider(providerId)
        requireAllowedBucket(provider, bucketName)
        val path = resolveObjectPath(provider, bucketName, key)
        val attributes = Files.readAttributes(path, BasicFileAttributes::class.java)
        val fileName = path.name.ifBlank { key }
        val contentType = detectContentType(provider, bucketName, key, path, fileName)

        if (!isAvroPreviewSupported(fileName, contentType)) {
            throw S3ViewerException("Object '$key' is not a supported Avro preview type")
        }

        try {
            val schema = if (fileName.lowercase().endsWith(".avsc")) {
                Files.newInputStream(path).use { input ->
                    AvroSchema.Parser().parse(input).toString(emptyList<AvroSchema>(), true)
                }
            } else {
                SeekablePathInput(path, attributes.size()).use { input ->
                    DataFileReader.openReader<GenericRecord>(input, GenericDatumReader()).use { reader ->
                        reader.getSchema().toString(emptyList<AvroSchema>(), true)
                    }
                }
            }
            return AvroSchemaPreview(
                key = key,
                fileName = fileName,
                size = attributes.size(),
                schema = schema
            )
        } catch (ex: Exception) {
            throw S3ViewerException("Failed to read Avro schema for object '$key'", ex)
        }
    }

    override fun previewAvroData(providerId: String, bucketName: String, key: String, maxRecords: Int): AvroDataPreview {
        val provider = getProvider(providerId)
        requireAllowedBucket(provider, bucketName)
        val path = resolveObjectPath(provider, bucketName, key)
        val attributes = Files.readAttributes(path, BasicFileAttributes::class.java)
        val fileName = path.name.ifBlank { key }
        val contentType = detectContentType(provider, bucketName, key, path, fileName)

        if (!isAvroDataPreviewSupported(fileName, contentType)) {
            throw S3ViewerException("Object '$key' is not a supported Avro data preview type")
        }

        val recordLimit = maxRecords.coerceIn(1, MAX_AVRO_PREVIEW_RECORDS)
        try {
            SeekablePathInput(path, attributes.size()).use { input ->
                DataFileReader.openReader<GenericRecord>(input, GenericDatumReader()).use { reader ->
                    val schema = reader.getSchema()
                    val rows = mutableListOf<String>()
                    while (reader.hasNext() && rows.size < recordLimit) {
                        rows.add(schema.recordToJson(reader.next()))
                    }
                    return AvroDataPreview(
                        key = key,
                        fileName = fileName,
                        size = attributes.size(),
                        schema = schema.toString(emptyList<AvroSchema>(), true),
                        truncated = reader.hasNext(),
                        recordCount = rows.size,
                        content = rows.toJsonArray()
                    )
                }
            }
        } catch (ex: Exception) {
            throw S3ViewerException("Failed to read Avro data for object '$key'", ex)
        }
    }

    open override fun createFolder(
        providerId: String,
        bucketName: String,
        path: String?,
        folderName: String
    ): BucketObjectEntry {
        val provider = getProvider(providerId)
        requireAllowedBucket(provider, bucketName)

        val normalizedPath = normalizePath(path)
        val normalizedFolderName = normalizeFolderName(folderName)
        val key = listOf(normalizedPath, normalizedFolderName)
            .filter { it.isNotBlank() }
            .joinToString("/")

        val fileSystem = fileSystem(provider, bucketName)
        val targetPath = fileSystem.getPath("/${key.trimStart('/')}").normalize()
        if (Files.exists(targetPath) && !Files.isDirectory(targetPath)) {
            throw S3ViewerException("Object '$key' already exists and is not a folder")
        }

        s3Client(provider).use { s3 ->
            s3.putObject(
                PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key("$key/")
                    .contentType("application/x-directory")
                    .build(),
                RequestBody.empty()
            )
        }

        return BucketObjectEntry(
            name = normalizedFolderName,
            key = key,
            type = BucketObjectType.DIRECTORY
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
        val prefix = directoryPrefix(normalizedPath)
        val lowerQuery = query.lowercase()

        val entries = linkedMapOf<String, BucketObjectEntry>()
        s3Client(provider).use { s3 ->
            val request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .build()

            s3.listObjectsV2Paginator(request).forEach { response ->
                response.contents().forEach { s3Object ->
                    val key = s3Object.key()
                    if (key == prefix) {
                        return@forEach
                    }

                    val relative = key.removePrefix(prefix).trim('/')
                    if (relative.isBlank()) {
                        return@forEach
                    }

                    val parts = relative.split('/').filter { it.isNotBlank() }
                    val directoryParts = if (key.endsWith("/")) parts else parts.dropLast(1)
                    var directoryKey = normalizedPath
                    directoryParts.forEach { part ->
                        directoryKey = listOf(directoryKey, part)
                            .filter { it.isNotBlank() }
                            .joinToString("/")
                        if (part.lowercase().contains(lowerQuery)) {
                            entries.putIfAbsent(
                                directoryKey,
                                BucketObjectEntry(
                                    name = part,
                                    key = directoryKey,
                                    type = BucketObjectType.DIRECTORY
                                )
                            )
                        }
                    }

                    if (!key.endsWith("/")) {
                        val name = parts.lastOrNull().orEmpty()
                        if (name.lowercase().contains(lowerQuery)) {
                            entries.putIfAbsent(key, s3Object.toFileEntry(key, name))
                        }
                    }
                }
            }
        }

        return SearchResult(
            providerId = provider.id,
            bucketName = bucketName,
            query = query,
            entries = entries.values
                .sortedWith(compareBy<BucketObjectEntry> { it.type != BucketObjectType.DIRECTORY }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                .take(maxResults)
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

    private fun directoryPrefix(path: String): String =
        if (path.isBlank()) "" else "$path/"

    private fun S3Client.directoryExists(bucketName: String, normalizedPath: String): Boolean {
        val prefix = directoryPrefix(normalizedPath)
        if (objectExists(bucketName, prefix)) {
            return true
        }

        val parent = parentPath(normalizedPath).orEmpty()
        val parentPrefix = directoryPrefix(parent)
        val response = try {
            listObjectsV2(
                ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(parentPrefix)
                    .delimiter("/")
                    .build()
            )
        } catch (ex: S3Exception) {
            if (ex.statusCode() == 404) {
                return false
            }
            throw ex
        }

        return response.commonPrefixes().any { it.prefix() == prefix } ||
                response.contents().any { it.key() == prefix }
    }

    private fun S3Client.objectExists(bucketName: String, key: String): Boolean =
        try {
            headObject(
                HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build()
            )
            true
        } catch (ex: S3Exception) {
            if (ex.statusCode() == 404) {
                false
            } else {
                throw ex
            }
        }

    private fun S3Object.toFileEntry(key: String, name: String): BucketObjectEntry =
        BucketObjectEntry(
            name = name,
            key = key,
            type = BucketObjectType.FILE,
            size = size(),
            lastModified = lastModified()
        )

    private fun normalizeFolderName(folderName: String): String {
        val normalized = folderName.trim().trim('/')
        if (normalized.isBlank()) {
            throw S3ViewerException("Folder name must not be blank")
        }
        if ('/' in normalized) {
            throw S3ViewerException("Folder name must not contain '/'")
        }
        return normalized
    }

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
            s3Client(provider).use { s3 ->
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

    private fun s3Client(provider: S3ViewerProperties.Provider): S3Client =
        S3Client.builder()
            .httpClientBuilder(
                ApacheHttpClient.builder()
                    .proxyConfiguration(
                        ProxyConfiguration.builder()
                            .useSystemPropertyValues(false)
                            .useEnvironmentVariableValues(false)
                            .build()
                    )
            )
            .region(Region.of(provider.region))
            .endpointOverride(URI.create(provider.endpoint))
            .forcePathStyle(provider.pathStyleAccess)
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(provider.accessKey, provider.secretKey)
                )
            )
            .build()

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

    private fun isAvroPreviewSupported(fileName: String, contentType: String): Boolean {
        val lowerName = fileName.lowercase()
        val lowerContentType = contentType.lowercase()
        return lowerName.endsWith(".avro") ||
                lowerName.endsWith(".avsc") ||
                lowerContentType in AVRO_CONTENT_TYPES
    }

    private fun isAvroDataPreviewSupported(fileName: String, contentType: String): Boolean {
        val lowerName = fileName.lowercase()
        val lowerContentType = contentType.lowercase()
        return lowerName.endsWith(".avro") ||
                lowerContentType in AVRO_DATA_CONTENT_TYPES
    }

    private fun AvroSchema.recordToJson(record: GenericRecord): String {
        val output = ByteArrayOutputStream()
        val encoder = EncoderFactory.get().jsonEncoder(this, output)
        GenericDatumWriter<GenericRecord>(this).write(record, encoder)
        encoder.flush()
        return output.toString(Charsets.UTF_8)
    }

    private fun List<String>.toJsonArray(): String =
        if (isEmpty()) {
            "[]"
        } else {
            joinToString(separator = ",\n", prefix = "[\n", postfix = "\n]") { "  $it" }
        }

    companion object {
        private const val MAX_TEXT_PREVIEW_BYTES = 1024 * 1024L
        private const val MAX_AVRO_PREVIEW_RECORDS = 1_000

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

        private val AVRO_CONTENT_TYPES = setOf(
            "application/avro",
            "application/avro-binary",
            "application/vnd.apache.avro",
            "application/x-avro",
            "application/x-avro-binary",
            "application/vnd.apache.avro+json",
            "application/x-avro-schema"
        )

        private val AVRO_DATA_CONTENT_TYPES = AVRO_CONTENT_TYPES - "application/x-avro-schema"
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

private class SeekablePathInput(
    path: Path,
    private val length: Long
) : SeekableInput {
    private val channel: SeekableByteChannel = Files.newByteChannel(path, StandardOpenOption.READ)

    override fun seek(p: Long) {
        channel.position(p)
    }

    override fun tell(): Long = channel.position()

    override fun length(): Long = length

    override fun read(b: ByteArray, off: Int, len: Int): Int =
        channel.read(ByteBuffer.wrap(b, off, len))

    override fun close() {
        channel.close()
    }
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
