package org.openprojectx.s3.viewer.core

import java.io.InputStream
import java.time.Instant

interface S3ViewerService {
    fun listProviders(): List<ViewerProvider>

    fun listBuckets(providerId: String): List<ViewerBucket>

    fun browseBucket(providerId: String, bucketName: String, path: String?): BucketBrowseResult

    fun downloadObject(providerId: String, bucketName: String, key: String): ObjectDownload

    fun uploadObject(providerId: String, bucketName: String, path: String?, fileName: String, inputStream: InputStream): BucketObjectEntry

    fun deleteObjects(providerId: String, bucketName: String, keys: List<String>)

    fun searchObjects(providerId: String, bucketName: String, query: String, path: String?, maxResults: Int): SearchResult
}

data class ViewerProvider(
    val id: String,
    val name: String,
    val endpoint: String,
    val region: String,
    val bucketCount: Int,
    val pathStyleAccess: Boolean
)

data class ViewerBucket(
    val providerId: String,
    val name: String,
    val configured: Boolean,
    val objectCountHint: Long? = null
)

data class BucketBrowseResult(
    val providerId: String,
    val bucketName: String,
    val path: String,
    val parentPath: String?,
    val entries: List<BucketObjectEntry>
)

data class BucketObjectEntry(
    val name: String,
    val key: String,
    val type: BucketObjectType,
    val size: Long? = null,
    val lastModified: Instant? = null
)

enum class BucketObjectType {
    DIRECTORY,
    FILE
}

data class ObjectDownload(
    val fileName: String,
    val contentType: String,
    val size: Long?,
    val inputStream: InputStream
)

data class SearchResult(
    val providerId: String,
    val bucketName: String,
    val query: String,
    val entries: List<BucketObjectEntry>
)
