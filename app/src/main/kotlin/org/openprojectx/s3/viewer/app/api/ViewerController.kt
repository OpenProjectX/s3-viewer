package org.openprojectx.s3.viewer.app.api

import org.openprojectx.s3.viewer.app.model.BrowseResponse
import org.openprojectx.s3.viewer.app.model.BucketSummary
import org.openprojectx.s3.viewer.app.model.ObjectEntry
import org.openprojectx.s3.viewer.app.model.ObjectEntryType
import org.openprojectx.s3.viewer.app.model.ProviderSummary
import org.openprojectx.s3.viewer.core.BucketBrowseResult
import org.openprojectx.s3.viewer.core.BucketObjectEntry
import org.openprojectx.s3.viewer.core.BucketObjectType
import org.openprojectx.s3.viewer.core.S3ViewerService
import org.openprojectx.s3.viewer.core.ViewerBucket
import org.openprojectx.s3.viewer.core.ViewerProvider
import org.springframework.http.ResponseEntity
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
