package org.openprojectx.s3.viewer.autoconfigure.api

import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.openprojectx.s3.viewer.autoconfigure.model.BrowseResponse
import org.openprojectx.s3.viewer.autoconfigure.model.BucketSummary
import org.openprojectx.s3.viewer.autoconfigure.model.DeleteRequest
import org.openprojectx.s3.viewer.autoconfigure.model.ObjectEntry
import org.openprojectx.s3.viewer.autoconfigure.model.ObjectEntryType
import org.openprojectx.s3.viewer.autoconfigure.model.ProviderSummary
import org.openprojectx.s3.viewer.autoconfigure.model.SearchResponse
import org.openprojectx.s3.viewer.core.BucketBrowseResult
import org.openprojectx.s3.viewer.core.BucketObjectEntry
import org.openprojectx.s3.viewer.core.BucketObjectType
import org.openprojectx.s3.viewer.core.S3ViewerService
import org.openprojectx.s3.viewer.core.SearchResult
import org.openprojectx.s3.viewer.core.ViewerBucket
import org.openprojectx.s3.viewer.core.ViewerProvider
import org.springframework.core.io.InputStreamResource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.http.codec.multipart.Part
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.ZoneOffset

@RestController
class ViewerController(
    private val s3ViewerService: S3ViewerService
) : ViewerApi {

    override fun listProviders(exchange: ServerWebExchange): Mono<ResponseEntity<Flux<ProviderSummary>>> =
        Mono.fromSupplier {
            ResponseEntity.ok(Flux.fromIterable(s3ViewerService.listProviders().map(ViewerProvider::toApiModel)))
        }

    override fun listBuckets(
        providerId: String,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<Flux<BucketSummary>>> =
        Mono.fromSupplier {
            ResponseEntity.ok(Flux.fromIterable(s3ViewerService.listBuckets(providerId).map(ViewerBucket::toApiModel)))
        }

    override fun browseBucket(
        providerId: String,
        bucketName: String,
        path: String?,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<BrowseResponse>> =
        Mono.fromSupplier {
            ResponseEntity.ok(s3ViewerService.browseBucket(providerId, bucketName, path).toApiModel())
        }

    override fun downloadObject(
        providerId: String,
        bucketName: String,
        key: String,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<org.springframework.core.io.Resource>> =
        Mono.fromSupplier {
            val download = s3ViewerService.downloadObject(providerId, bucketName, key)
            val headers = HttpHeaders().apply {
                contentType = MediaType.parseMediaType(download.contentType)
                contentDisposition = ContentDisposition.attachment().filename(download.fileName).build()
                download.size?.let { contentLength = it }
            }
            ResponseEntity.ok()
                .headers(headers)
                .body(InputStreamResource(download.inputStream))
        }

    override fun uploadObject(
        providerId: @NotNull String,
        bucketName: @NotNull String,
        filePart: Part,
        path: @Valid String?,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ObjectEntry>> {
        val fileName = (filePart as FilePart).filename()
        return filePart.content().reduce(
            java.io.ByteArrayOutputStream(),
            { baos, dataBuffer ->
                val bytes = ByteArray(dataBuffer.readableByteCount())
                dataBuffer.read(bytes)
                org.springframework.core.io.buffer.DataBufferUtils.release(dataBuffer)
                baos.write(bytes)
                baos
            }
        ).map { baos ->
            val entry = s3ViewerService.uploadObject(
                providerId, bucketName, path, fileName, baos.toByteArray().inputStream()
            )
            ResponseEntity.status(HttpStatus.CREATED).body(entry.toApiModel())
        }
    }

    override fun deleteObjects(
        providerId: String,
        bucketName: String,
        deleteRequest: Mono<DeleteRequest>,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<Void>> =
        deleteRequest.map { request ->
            s3ViewerService.deleteObjects(providerId, bucketName, request.keys)
            ResponseEntity.noContent().build()
        }

    override fun searchObjects(
        providerId: String,
        bucketName: String,
        query: String,
        path: String?,
        maxResults: Int?,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<SearchResponse>> =
        Mono.fromSupplier {
            val result = s3ViewerService.searchObjects(
                providerId, bucketName, query, path, maxResults ?: 100
            )
            ResponseEntity.ok(result.toApiModel())
        }
}

private fun ViewerProvider.toApiModel(): ProviderSummary =
    ProviderSummary()
        .id(id)
        .name(name)
        .endpoint(endpoint)
        .region(region)
        .bucketCount(bucketCount)
        .pathStyleAccess(pathStyleAccess)

private fun ViewerBucket.toApiModel(): BucketSummary =
    BucketSummary()
        .providerId(providerId)
        .name(name)
        .configured(configured)
        .objectCountHint(objectCountHint)

private fun BucketBrowseResult.toApiModel(): BrowseResponse =
    BrowseResponse()
        .providerId(providerId)
        .bucketName(bucketName)
        .path(path)
        .parentPath(parentPath)
        .entries(entries.map(BucketObjectEntry::toApiModel))

private fun SearchResult.toApiModel(): SearchResponse =
    SearchResponse()
        .providerId(providerId)
        .bucketName(bucketName)
        .query(query)
        .entries(entries.map(BucketObjectEntry::toApiModel))

private fun BucketObjectEntry.toApiModel(): ObjectEntry =
    ObjectEntry()
        .name(name)
        .key(key)
        .type(type.toApiModel())
        .size(size)
        .lastModified(lastModified?.atOffset(ZoneOffset.UTC))

private fun BucketObjectType.toApiModel(): ObjectEntryType =
    when (this) {
        BucketObjectType.DIRECTORY -> ObjectEntryType.DIRECTORY
        BucketObjectType.FILE -> ObjectEntryType.FILE
    }
