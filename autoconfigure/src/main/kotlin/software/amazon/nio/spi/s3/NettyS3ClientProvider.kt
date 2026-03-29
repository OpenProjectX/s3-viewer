package software.amazon.nio.spi.s3

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.Protocol
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.nio.spi.s3.config.S3NioSpiConfiguration
import java.time.Duration

internal class NettyS3ClientProvider(
    private val config: S3NioSpiConfiguration
) : S3ClientProvider(config) {

    override fun generateClient(bucketName: String): S3AsyncClient {
        val httpClient = NettyNioAsyncHttpClient.builder()
            .protocol(Protocol.HTTP1_1)
            .connectionTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofMinutes(config.getTimeoutLow()))
            .writeTimeout(Duration.ofMinutes(config.getTimeoutLow()))
            .build()

        val builder = S3AsyncClient.builder()
            .httpClient(httpClient)
            .region(Region.of(config.getRegion()))
            .forcePathStyle(config.getForcePathStyle())

        config.endpointUri()?.let { builder.endpointOverride(it) }

        config.getCredentials()?.let { credentials ->
            builder.credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        credentials.accessKeyId(),
                        credentials.secretAccessKey()
                    )
                )
            )
        }

        return builder.build()
    }
}