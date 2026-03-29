package software.amazon.nio.spi.s3

import org.openprojectx.s3.viewer.autoconfigure.S3ViewerProperties
import software.amazon.nio.spi.s3.config.S3NioSpiConfiguration
import software.amazon.nio.spi.s3.util.S3FileSystemInfo
import java.net.URI
import java.nio.file.FileSystem
import java.util.concurrent.ConcurrentHashMap

class SpringAwareS3FileSystemProvider(
    private val providerProperties: S3ViewerProperties.Provider
) : S3FileSystemProvider() {

    private val fileSystems = ConcurrentHashMap<String, S3FileSystem>()

    override fun getScheme(): String = "s3"

    override fun getFileSystem(uri: URI): FileSystem {
        val bucket = bucketName(uri)
        requireAllowedBucket(bucket)

        return fileSystems.computeIfAbsent(bucket) {
            val endpointUri = URI.create(providerProperties.endpoint)
            val overrides = linkedMapOf<String, Any>(
                S3NioSpiConfiguration.AWS_REGION_PROPERTY to providerProperties.region,
                S3NioSpiConfiguration.AWS_ACCESS_KEY_PROPERTY to providerProperties.accessKey,
                S3NioSpiConfiguration.AWS_SECRET_ACCESS_KEY_PROPERTY to providerProperties.secretKey,
                S3NioSpiConfiguration.S3_SPI_ENDPOINT_PROPERTY to endpointHostPort(providerProperties.endpoint),
                S3NioSpiConfiguration.S3_SPI_ENDPOINT_PROTOCOL_PROPERTY to (endpointUri.scheme ?: "https"),
                S3NioSpiConfiguration.S3_SPI_FORCE_PATH_STYLE_PROPERTY to providerProperties.pathStyleAccess.toString()
            )

            val config = S3NioSpiConfiguration(overrides)
                .withBucketName(bucket)

            S3FileSystem(this, config).also { fs ->
                fs.clientProvider(NettyS3ClientProvider(config))
            }
        }
    }

    override fun fileSystemInfo(uri: URI): S3FileSystemInfo {
        val bucket = bucketName(uri)
        requireAllowedBucket(bucket)

        return SpringS3FileSystemInfo(
            fsKey = "${providerProperties.id}::$bucket",
            fsBucket = bucket,
            fsEndpoint = endpointHostPort(providerProperties.endpoint),
            fsAccessKey = providerProperties.accessKey,
            fsAccessSecret = providerProperties.secretKey
        )
    }

    private fun requireAllowedBucket(bucket: String) {
        if (providerProperties.buckets.isNotEmpty() && bucket !in providerProperties.buckets) {
            throw IllegalArgumentException(
                "Bucket '$bucket' is not configured for provider '${providerProperties.id}'"
            )
        }
    }

    private fun bucketName(uri: URI): String {
        require(uri.scheme == "s3") { "URI scheme must be s3" }
        return uri.host?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Bucket name is missing in URI: $uri")
    }

    private fun endpointHostPort(endpoint: String): String {
        val uri = URI.create(endpoint)
        val host = uri.host ?: throw IllegalArgumentException("Endpoint host is missing: $endpoint")
        return if (uri.port == -1) host else "$host:${uri.port}"
    }
}