package org.openprojectx.s3.viewer.app

import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.localstack.LocalStackContainer.Service.S3
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.S3Exception

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class S3ViewerLocalStackIntegrationTest {

    companion object {
        @Container
        @JvmField
        val localstack: LocalStackContainer =
            LocalStackContainer(
                DockerImageName.parse("ghcr.io/openprojectx/dockerhub/localstack/localstack:4")
                    .asCompatibleSubstituteFor("localstack/localstack")
            )
                .withServices(S3)

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("s3-viewer.providers[0].id") { "test" }
            registry.add("s3-viewer.providers[0].name") { "Test LocalStack" }
            registry.add("s3-viewer.providers[0].region") { "us-east-1" }
            registry.add("s3-viewer.providers[0].access-key") { "test" }
            registry.add("s3-viewer.providers[0].secret-key") { "test" }
            registry.add("s3-viewer.providers[0].path-style-access") { true }
            registry.add("s3-viewer.providers[0].buckets[0]") { TEST_BUCKET }
            registry.add("s3-viewer.providers[0].endpoint") {
                startLocalstackIfNecessary()
                localstack.getEndpointOverride(S3).toString()
            }
        }

        private fun startLocalstackIfNecessary() {
            if (!localstack.isRunning) {
                localstack.start()
            }
        }
    }

    protected fun s3Client(): S3Client {
        startLocalstackIfNecessary()
        return S3Client.builder()
            .endpointOverride(localstack.getEndpointOverride(S3))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
            .region(Region.US_EAST_1)
            .forcePathStyle(true)
            .build()
    }

    protected fun ensureTestBucketExists(s3: S3Client) {
        try {
            s3.createBucket(CreateBucketRequest.builder().bucket(TEST_BUCKET).build())
        } catch (exception: S3Exception) {
            if (exception.statusCode() != 409) {
                throw exception
            }
        }
    }
}

private const val TEST_BUCKET = "test-bucket"
