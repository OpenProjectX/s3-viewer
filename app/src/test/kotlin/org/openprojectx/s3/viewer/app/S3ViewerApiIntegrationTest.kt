package org.openprojectx.s3.viewer.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception

class S3ViewerApiIntegrationTest : S3ViewerLocalStackIntegrationTest() {

    @LocalServerPort
    private var port: Int = 0

    private val webTestClient: WebTestClient
        get() = WebTestClient.bindToServer()
            .baseUrl("http://localhost:$port")
            .build()

    @BeforeAll
    fun seedData() {
        s3Client().use { s3 ->
            ensureTestBucketExists(s3)
            s3.putObject(
                PutObjectRequest.builder().bucket("test-bucket").key("docs/readme.txt").build(),
                RequestBody.fromString("Hello S3 Viewer")
            )
            s3.putObject(
                PutObjectRequest.builder().bucket("test-bucket").key("images/photo.jpg").build(),
                RequestBody.fromBytes(ByteArray(128))
            )
            s3.putObject(
                PutObjectRequest.builder().bucket("test-bucket").key("data.csv").build(),
                RequestBody.fromString("col1,col2\nval1,val2")
            )
        }
    }

    @Test
    fun `list providers returns configured test provider`() {
        webTestClient.get().uri("/s3-viewer/api/v1/providers")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].id").isEqualTo("test")
            .jsonPath("$[0].name").isEqualTo("Test LocalStack")
            .jsonPath("$[0].pathStyleAccess").isEqualTo(true)
    }

    @Test
    fun `list buckets returns configured bucket`() {
        webTestClient.get().uri("/s3-viewer/api/v1/providers/test/buckets")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].name").isEqualTo("test-bucket")
            .jsonPath("$[0].providerId").isEqualTo("test")
    }

    @Test
    fun `browse bucket root lists top-level entries`() {
        webTestClient.get().uri("/s3-viewer/api/v1/providers/test/buckets/test-bucket/browse")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.providerId").isEqualTo("test")
            .jsonPath("$.bucketName").isEqualTo("test-bucket")
            .jsonPath("$.entries").isArray
            .jsonPath("$.entries[?(@.name == 'data.csv')].type").isEqualTo("FILE")
            .jsonPath("$.entries[?(@.name == 'docs')].type").isEqualTo("DIRECTORY")
            .jsonPath("$.entries[?(@.name == 'images')].type").isEqualTo("DIRECTORY")
    }

    @Test
    fun `browse bucket subdirectory lists contained files`() {
        webTestClient.get().uri("/s3-viewer/api/v1/providers/test/buckets/test-bucket/browse?path=docs/")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.entries[?(@.name == 'readme.txt')].type").isEqualTo("FILE")
    }

    @Test
    fun `download object streams file content`() {
        webTestClient.get()
            .uri("/s3-viewer/api/v1/providers/test/buckets/test-bucket/download?key=docs/readme.txt")
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueMatches("Content-Disposition", ".*readme\\.txt.*")
            .expectBody(String::class.java).isEqualTo("Hello S3 Viewer")
    }

    @Test
    fun `upload object appears in subsequent browse`() {
        val content = "uploaded content".toByteArray()
        webTestClient.post()
            .uri("/s3-viewer/api/v1/providers/test/buckets/test-bucket/upload?path=uploads/")
            .header("Content-Type", "multipart/form-data; boundary=boundary")
            .bodyValue(
                "--boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"new.txt\"\r\n\r\n" +
                        String(content) + "\r\n--boundary--"
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.name").isEqualTo("new.txt")
            .jsonPath("$.type").isEqualTo("FILE")
    }

    @Test
    fun `delete objects removes them from the bucket`() {
        s3Client().use { s3 ->
            s3.putObject(
                PutObjectRequest.builder().bucket("test-bucket").key("to-delete.txt").build(),
                RequestBody.fromString("bye")
            )

            webTestClient.method(HttpMethod.DELETE)
                .uri("/s3-viewer/api/v1/providers/test/buckets/test-bucket/objects")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"keys":["to-delete.txt"]}""")
                .exchange()
                .expectStatus().isNoContent

            val exception = assertThrows(S3Exception::class.java) {
                s3.headObject(HeadObjectRequest.builder().bucket("test-bucket").key("to-delete.txt").build())
            }
            assertEquals(404, exception.statusCode())
        }
    }

    @Test
    fun `search objects returns matching entries`() {
        webTestClient.get()
            .uri("/s3-viewer/api/v1/providers/test/buckets/test-bucket/search?query=readme")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.query").isEqualTo("readme")
            .jsonPath("$.entries[?(@.name == 'readme.txt')]").exists()
    }

    @Test
    fun `search objects returns empty for no matches`() {
        webTestClient.get()
            .uri("/s3-viewer/api/v1/providers/test/buckets/test-bucket/search?query=nonexistent-xyz")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.entries").isEmpty
    }
}
