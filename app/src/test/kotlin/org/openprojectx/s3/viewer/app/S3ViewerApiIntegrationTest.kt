package org.openprojectx.s3.viewer.app

import org.apache.avro.Schema
import org.apache.avro.file.DataFileWriter
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericDatumWriter
import org.apache.parquet.avro.AvroParquetWriter
import org.apache.parquet.hadoop.metadata.CompressionCodecName
import org.apache.parquet.io.OutputFile
import org.apache.parquet.io.PositionOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception

class S3ViewerApiIntegrationTest : S3ViewerLocalStackIntegrationTest() {
    private val userSchemaJson = """
        {
          "type": "record",
          "name": "UserEvent",
          "namespace": "org.openprojectx.s3.viewer.test",
          "fields": [
            { "name": "id", "type": "string" },
            { "name": "enabled", "type": "boolean" }
          ]
        }
    """.trimIndent()

    private val userSchema = Schema.Parser().parse(userSchemaJson)

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
            s3.createBucketIfMissing("archive-bucket")
            s3.putObject(
                PutObjectRequest.builder().bucket("test-bucket").key("docs/readme.txt").build(),
                RequestBody.fromString("Hello S3 Viewer")
            )
            s3.putObject(
                PutObjectRequest.builder()
                    .bucket("test-bucket")
                    .key("docs/plain.text")
                    .contentType("application/octet-stream")
                    .build(),
                RequestBody.fromString("Plain text extension")
            )
            s3.putObject(
                PutObjectRequest.builder()
                    .bucket("test-bucket")
                    .key("docs/config.json")
                    .contentType("application/octet-stream")
                    .build(),
                RequestBody.fromString("""{"enabled":true}""")
            )
            s3.putObject(
                PutObjectRequest.builder()
                    .bucket("test-bucket")
                    .key("docs/reports/")
                    .contentType("application/x-directory")
                    .build(),
                RequestBody.empty()
            )
            s3.putObject(
                PutObjectRequest.builder().bucket("test-bucket").key("images/photo.jpg").build(),
                RequestBody.fromBytes(ByteArray(128))
            )
            s3.putObject(
                PutObjectRequest.builder().bucket("test-bucket").key("warehouse/hive-parquet/data.parquet").build(),
                RequestBody.fromBytes(parquetBytes())
            )
            s3.putObject(
                PutObjectRequest.builder().bucket("test-bucket").key("schemas/user-event.avsc").build(),
                RequestBody.fromString(userSchemaJson)
            )
            s3.putObject(
                PutObjectRequest.builder().bucket("test-bucket").key("warehouse/avro/user-event.avro").build(),
                RequestBody.fromBytes(avroContainerBytes())
            )
            s3.putObject(
                PutObjectRequest.builder().bucket("test-bucket").key("data.csv").build(),
                RequestBody.fromString("col1,col2\nval1,val2")
            )
        }
    }

    @Test
    fun `api crud flow lists browses downloads uploads searches and deletes objects`() {
        webTestClient.get().uri("/s3-viewer/api/v1/providers")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].id").isEqualTo("test")
            .jsonPath("$[0].name").isEqualTo("Test LocalStack")
            .jsonPath("$[0].pathStyleAccess").isEqualTo(true)

        webTestClient.get().uri("/s3-viewer/api/v1/providers/test/buckets")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[?(@.name == 'test-bucket')].providerId").isEqualTo("test")
            .jsonPath("$[?(@.name == 'archive-bucket')].configured").isEqualTo(false)
            .jsonPath("$[?(@.name == 'test-bucket')].configured").isEqualTo(false)

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

        webTestClient.get().uri("/s3-viewer/api/v1/providers/test/buckets/test-bucket/browse?path=docs/")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.entries[?(@.name == 'readme.txt')].type").isEqualTo("FILE")
            .jsonPath("$.entries[?(@.name == 'config.json')].type").isEqualTo("FILE")
            .jsonPath("$.entries[?(@.name == 'reports')].type").isEqualTo("DIRECTORY")

        webTestClient.get().uri("/s3-viewer/api/v1/providers/test/buckets/test-bucket/browse?path=docs/reports")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.path").isEqualTo("docs/reports")
            .jsonPath("$.entries").isEmpty

        webTestClient.get().uri("/s3-viewer/api/v1/providers/test/buckets/test-bucket/browse?path=warehouse/hive-parquet")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.path").isEqualTo("warehouse/hive-parquet")
            .jsonPath("$.entries[?(@.name == 'data.parquet')].type").isEqualTo("FILE")

        webTestClient.get()
            .uri("/s3-viewer/api/v1/providers/test/buckets/test-bucket/download?key=docs/readme.txt")
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueMatches("Content-Disposition", ".*readme\\.txt.*")
            .expectBody(String::class.java).isEqualTo("Hello S3 Viewer")

        webTestClient.get()
            .uri("/s3-viewer/api/v1/providers/test/buckets/test-bucket/preview/text?key=docs/readme.txt")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.key").isEqualTo("docs/readme.txt")
            .jsonPath("$.fileName").isEqualTo("readme.txt")
            .jsonPath("$.contentType").value<String> { assertEquals(true, it.startsWith("text/plain")) }
            .jsonPath("$.truncated").isEqualTo(false)
            .jsonPath("$.content").isEqualTo("Hello S3 Viewer")

        webTestClient.get()
            .uri("/s3-viewer/api/v1/providers/test/buckets/test-bucket/preview/text?key=docs/config.json")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.fileName").isEqualTo("config.json")
            .jsonPath("$.contentType").isEqualTo("application/octet-stream")
            .jsonPath("$.truncated").isEqualTo(false)
            .jsonPath("$.content").isEqualTo("""{"enabled":true}""")

        webTestClient.get()
            .uri("/s3-viewer/api/v1/providers/test/buckets/test-bucket/preview/text?key=docs/plain.text")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.fileName").isEqualTo("plain.text")
            .jsonPath("$.contentType").isEqualTo("application/octet-stream")
            .jsonPath("$.truncated").isEqualTo(false)
            .jsonPath("$.content").isEqualTo("Plain text extension")

        webTestClient.get()
            .uri("/s3-viewer/api/v1/providers/test/buckets/test-bucket/preview/parquet/schema?key=warehouse/hive-parquet/data.parquet")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.fileName").isEqualTo("data.parquet")
            .jsonPath("$.schema").value<String> { assertEquals(true, it.contains("UserEvent")) }
            .jsonPath("$.schema").value<String> { assertEquals(true, it.contains("enabled")) }

        webTestClient.get()
            .uri("/s3-viewer/api/v1/providers/test/buckets/test-bucket/preview/parquet/data?key=warehouse/hive-parquet/data.parquet&maxRecords=1")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.fileName").isEqualTo("data.parquet")
            .jsonPath("$.recordCount").isEqualTo(1)
            .jsonPath("$.truncated").isEqualTo(true)
            .jsonPath("$.schema").value<String> { assertEquals(true, it.contains("UserEvent")) }
            .jsonPath("$.content").value<String> { assertEquals(true, it.contains("evt-1")) }
            .jsonPath("$.content").value<String> { assertEquals(true, it.contains("enabled")) }

        webTestClient.get()
            .uri("/s3-viewer/api/v1/providers/test/buckets/test-bucket/preview/avro/schema?key=schemas/user-event.avsc")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.fileName").isEqualTo("user-event.avsc")
            .jsonPath("$.schema").value<String> { assertEquals(true, it.contains("UserEvent")) }
            .jsonPath("$.schema").value<String> { assertEquals(true, it.contains("enabled")) }

        webTestClient.get()
            .uri("/s3-viewer/api/v1/providers/test/buckets/test-bucket/preview/avro/schema?key=warehouse/avro/user-event.avro")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.fileName").isEqualTo("user-event.avro")
            .jsonPath("$.schema").value<String> { assertEquals(true, it.contains("UserEvent")) }
            .jsonPath("$.schema").value<String> { assertEquals(true, it.contains("enabled")) }

        webTestClient.get()
            .uri("/s3-viewer/api/v1/providers/test/buckets/test-bucket/preview/avro/data?key=warehouse/avro/user-event.avro&maxRecords=1")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.fileName").isEqualTo("user-event.avro")
            .jsonPath("$.recordCount").isEqualTo(1)
            .jsonPath("$.truncated").isEqualTo(true)
            .jsonPath("$.schema").value<String> { assertEquals(true, it.contains("UserEvent")) }
            .jsonPath("$.content").value<String> { assertEquals(true, it.contains("evt-1")) }
            .jsonPath("$.content").value<String> { assertEquals(true, it.contains("enabled")) }

        webTestClient.post()
            .uri("/s3-viewer/api/v1/providers/test/buckets/test-bucket/folders")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"path":"docs","folderName":"adhoc"}""")
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.name").isEqualTo("adhoc")
            .jsonPath("$.key").isEqualTo("docs/adhoc")
            .jsonPath("$.type").isEqualTo("DIRECTORY")

        webTestClient.get().uri("/s3-viewer/api/v1/providers/test/buckets/test-bucket/browse?path=docs/")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.entries[?(@.name == 'adhoc')].type").isEqualTo("DIRECTORY")

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

        webTestClient.get().uri("/s3-viewer/api/v1/providers/test/buckets/test-bucket/browse?path=uploads/")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.entries[?(@.name == 'new.txt')].type").isEqualTo("FILE")

        webTestClient.get()
            .uri("/s3-viewer/api/v1/providers/test/buckets/test-bucket/search?query=readme")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.query").isEqualTo("readme")
            .jsonPath("$.entries[?(@.name == 'readme.txt')]").exists()

        webTestClient.get()
            .uri("/s3-viewer/api/v1/providers/test/buckets/test-bucket/search?query=nonexistent-xyz")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.entries").isEmpty

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

    private fun avroContainerBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        DataFileWriter(GenericDatumWriter<GenericData.Record>(userSchema)).use { writer ->
            writer.create(userSchema, output)
            val record = GenericData.Record(userSchema).apply {
                put("id", "evt-1")
                put("enabled", true)
            }
            writer.append(record)
            val secondRecord = GenericData.Record(userSchema).apply {
                put("id", "evt-2")
                put("enabled", false)
            }
            writer.append(secondRecord)
        }
        return output.toByteArray()
    }

    private fun parquetBytes(): ByteArray {
        val output = ByteArrayOutputFile()
        AvroParquetWriter.builder<GenericData.Record>(output)
            .withSchema(userSchema)
            .withCompressionCodec(CompressionCodecName.UNCOMPRESSED)
            .build()
            .use { writer ->
                val record = GenericData.Record(userSchema).apply {
                    put("id", "evt-1")
                    put("enabled", true)
                }
                writer.write(record)
                val secondRecord = GenericData.Record(userSchema).apply {
                    put("id", "evt-2")
                    put("enabled", false)
                }
                writer.write(secondRecord)
            }
        return output.toByteArray()
    }

    private fun S3Client.createBucketIfMissing(bucketName: String) {
        try {
            createBucket(CreateBucketRequest.builder().bucket(bucketName).build())
        } catch (exception: S3Exception) {
            if (exception.statusCode() != 409) {
                throw exception
            }
        }
    }
}

private class ByteArrayOutputFile : OutputFile {
    private val output = ByteArrayOutputStream()

    override fun create(blockSizeHint: Long): PositionOutputStream = outputStream()

    override fun createOrOverwrite(blockSizeHint: Long): PositionOutputStream = outputStream()

    override fun supportsBlockSize(): Boolean = false

    override fun defaultBlockSize(): Long = 0L

    fun toByteArray(): ByteArray = output.toByteArray()

    private fun outputStream(): PositionOutputStream =
        object : PositionOutputStream() {
            override fun getPos(): Long = output.size().toLong()

            override fun write(value: Int) {
                output.write(value)
            }

            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                output.write(bytes, offset, length)
            }
        }
}
