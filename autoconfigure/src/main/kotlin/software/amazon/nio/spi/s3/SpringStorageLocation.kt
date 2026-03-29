package software.amazon.nio.spi.s3

import java.net.URI

internal data class SpringStorageLocation(
    val providerId: String,
    val bucket: String,
    val key: String
) {
    companion object {
        fun from(uri: URI): SpringStorageLocation {
            require(uri.scheme == "s3") { "Unsupported scheme '${uri.scheme}'" }

            val providerId = uri.host?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Provider id is missing in URI host")

            val segments = uri.path
                .trim('/')
                .split('/')
                .filter { it.isNotBlank() }

            require(segments.isNotEmpty()) { "Bucket is missing in URI path" }

            val bucket = segments.first()
            val key = segments.drop(1).joinToString("/")

            return SpringStorageLocation(
                providerId = providerId,
                bucket = bucket,
                key = key
            )
        }
    }
}