package org.openprojectx.s3.viewer.autoconfigure.api

import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.openprojectx.s3.viewer.autoconfigure.model.BrowseResponse
import org.openprojectx.s3.viewer.autoconfigure.model.BucketSummary
import org.openprojectx.s3.viewer.autoconfigure.model.CreateFolderRequest
import org.openprojectx.s3.viewer.autoconfigure.model.DeleteRequest
import org.openprojectx.s3.viewer.autoconfigure.model.ObjectEntry
import org.openprojectx.s3.viewer.autoconfigure.model.ObjectEntryType
import org.openprojectx.s3.viewer.autoconfigure.model.AvroDataPreviewResponse
import org.openprojectx.s3.viewer.autoconfigure.model.AvroSchemaPreviewResponse
import org.openprojectx.s3.viewer.autoconfigure.model.ParquetDataPreviewResponse
import org.openprojectx.s3.viewer.autoconfigure.model.ParquetSchemaPreviewResponse
import org.openprojectx.s3.viewer.autoconfigure.model.ProviderSummary
import org.openprojectx.s3.viewer.autoconfigure.model.SearchResponse
import org.openprojectx.s3.viewer.autoconfigure.model.TextPreviewResponse
import org.openprojectx.s3.viewer.core.AvroDataPreview
import org.openprojectx.s3.viewer.core.AvroSchemaPreview
import org.openprojectx.s3.viewer.core.BucketBrowseResult
import org.openprojectx.s3.viewer.core.BucketObjectEntry
import org.openprojectx.s3.viewer.core.BucketObjectType
import org.openprojectx.s3.viewer.core.ParquetDataPreview
import org.openprojectx.s3.viewer.core.ParquetSchemaPreview
import org.openprojectx.s3.viewer.core.S3ViewerService
import org.openprojectx.s3.viewer.core.SearchResult
import org.openprojectx.s3.viewer.core.TextObjectPreview
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
import reactor.core.scheduler.Schedulers
import java.time.ZoneOffset

@RestController
class ViewerController(
    private val s3ViewerService: S3ViewerService
) : ViewerApi {

    override fun listProviders(exchange: ServerWebExchange): Mono<ResponseEntity<Flux<ProviderSummary>>> =
        blocking {
            ResponseEntity.ok(Flux.fromIterable(s3ViewerService.listProviders().map(ViewerProvider::toApiModel)))
        }

    override fun listBuckets(
        providerId: String,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<Flux<BucketSummary>>> =
        blocking {
            ResponseEntity.ok(Flux.fromIterable(s3ViewerService.listBuckets(providerId).map(ViewerBucket::toApiModel)))
        }

    override fun browseBucket(
        providerId: String,
        bucketName: String,
        path: String?,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<BrowseResponse>> =
        blocking {
            ResponseEntity.ok(s3ViewerService.browseBucket(providerId, bucketName, path).toApiModel())
        }

    override fun downloadObject(
        providerId: String,
        bucketName: String,
        key: String,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<org.springframework.core.io.Resource>> =
        blocking {
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

    override fun previewTextObject(
        providerId: String,
        bucketName: String,
        key: String,
        maxBytes: Long?,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<TextPreviewResponse>> =
        blocking {
            ResponseEntity.ok(
                s3ViewerService.previewTextObject(
                    providerId = providerId,
                    bucketName = bucketName,
                    key = key,
                    maxBytes = maxBytes ?: 1024 * 1024L
                ).toApiModel()
            )
        }

    override fun previewParquetSchema(
        providerId: String,
        bucketName: String,
        key: String,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ParquetSchemaPreviewResponse>> =
        blocking {
            ResponseEntity.ok(
                s3ViewerService.previewParquetSchema(providerId, bucketName, key).toApiModel()
            )
        }

    override fun previewParquetData(
        providerId: String,
        bucketName: String,
        key: String,
        maxRecords: Int?,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ParquetDataPreviewResponse>> =
        blocking {
            ResponseEntity.ok(
                s3ViewerService.previewParquetData(
                    providerId = providerId,
                    bucketName = bucketName,
                    key = key,
                    maxRecords = maxRecords ?: 100
                ).toApiModel()
            )
        }

    override fun previewAvroSchema(
        providerId: String,
        bucketName: String,
        key: String,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<AvroSchemaPreviewResponse>> =
        blocking {
            ResponseEntity.ok(
                s3ViewerService.previewAvroSchema(providerId, bucketName, key).toApiModel()
            )
        }

    override fun previewAvroData(
        providerId: String,
        bucketName: String,
        key: String,
        maxRecords: Int?,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<AvroDataPreviewResponse>> =
        blocking {
            ResponseEntity.ok(
                s3ViewerService.previewAvroData(
                    providerId = providerId,
                    bucketName = bucketName,
                    key = key,
                    maxRecords = maxRecords ?: 100
                ).toApiModel()
            )
        }

    override fun createFolder(
        providerId: String,
        bucketName: String,
        createFolderRequest: Mono<CreateFolderRequest>,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ObjectEntry>> =
        createFolderRequest.flatMap { request ->
            blocking {
                val entry = s3ViewerService.createFolder(
                    providerId = providerId,
                    bucketName = bucketName,
                    path = request.path,
                    folderName = request.folderName
                )
                ResponseEntity.status(HttpStatus.CREATED).body(entry.toApiModel())
            }
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
        ).flatMap { baos ->
            blocking {
                val entry = s3ViewerService.uploadObject(
                    providerId, bucketName, path, fileName, baos.toByteArray().inputStream()
                )
                ResponseEntity.status(HttpStatus.CREATED).body(entry.toApiModel())
            }
        }
    }

    override fun deleteObjects(
        providerId: String,
        bucketName: String,
        deleteRequest: Mono<DeleteRequest>,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<Void>> =
        deleteRequest.flatMap { request ->
            blocking {
                s3ViewerService.deleteObjects(providerId, bucketName, request.keys)
                ResponseEntity.noContent().build()
            }
        }

    override fun searchObjects(
        providerId: String,
        bucketName: String,
        query: String,
        path: String?,
        maxResults: Int?,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<SearchResponse>> =
        blocking {
            val result = s3ViewerService.searchObjects(
                providerId, bucketName, query, path, maxResults ?: 100
            )
            ResponseEntity.ok(result.toApiModel())
        }
}

private fun <T : Any> blocking(supplier: () -> T): Mono<T> =
    Mono.fromCallable(supplier).subscribeOn(Schedulers.boundedElastic())

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

private fun TextObjectPreview.toApiModel(): TextPreviewResponse =
    TextPreviewResponse()
        .key(key)
        .fileName(fileName)
        .contentType(contentType)
        .size(size)
        .truncated(truncated)
        .content(content)

private fun ParquetSchemaPreview.toApiModel(): ParquetSchemaPreviewResponse =
    ParquetSchemaPreviewResponse()
        .key(key)
        .fileName(fileName)
        .size(size)
        .schema(schema)

private fun ParquetDataPreview.toApiModel(): ParquetDataPreviewResponse =
    ParquetDataPreviewResponse()
        .key(key)
        .fileName(fileName)
        .size(size)
        .schema(schema)
        .truncated(truncated)
        .recordCount(recordCount)
        .content(content)

private fun AvroSchemaPreview.toApiModel(): AvroSchemaPreviewResponse =
    AvroSchemaPreviewResponse()
        .key(key)
        .fileName(fileName)
        .size(size)
        .schema(schema)

private fun AvroDataPreview.toApiModel(): AvroDataPreviewResponse =
    AvroDataPreviewResponse()
        .key(key)
        .fileName(fileName)
        .size(size)
        .schema(schema)
        .truncated(truncated)
        .recordCount(recordCount)
        .content(content)

private fun BucketObjectType.toApiModel(): ObjectEntryType =
    when (this) {
        BucketObjectType.DIRECTORY -> ObjectEntryType.DIRECTORY
        BucketObjectType.FILE -> ObjectEntryType.FILE
    }
