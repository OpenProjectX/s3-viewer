package org.openprojectx.s3.viewer.app

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters

@SpringBootTest(
    webEnvironment = RANDOM_PORT,
    properties = ["s3-viewer.read-only-access=true"]
)
@ActiveProfiles("test")
class S3ViewerReadOnlyIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    private val webTestClient: WebTestClient
        get() = WebTestClient.bindToServer()
            .baseUrl("http://localhost:$port")
            .build()

    @Test
    fun `upload is forbidden when readonly access is enabled`() {
        val file = object : ByteArrayResource("readonly upload".toByteArray()) {
            override fun getFilename(): String = "blocked.txt"
        }

        webTestClient.post()
            .uri("/s3-viewer/api/v1/providers/test/buckets/test-bucket/upload")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData("file", file))
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.message").isEqualTo("S3 Viewer is configured for read-only access")
    }

    @Test
    fun `create folder is forbidden when readonly access is enabled`() {
        webTestClient.post()
            .uri("/s3-viewer/api/v1/providers/test/buckets/test-bucket/folders")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"folderName":"blocked"}""")
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.message").isEqualTo("S3 Viewer is configured for read-only access")
    }

    @Test
    fun `delete is forbidden when readonly access is enabled`() {
        webTestClient.method(HttpMethod.DELETE)
            .uri("/s3-viewer/api/v1/providers/test/buckets/test-bucket/objects")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"keys":["blocked.txt"]}""")
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.message").isEqualTo("S3 Viewer is configured for read-only access")
    }
}
