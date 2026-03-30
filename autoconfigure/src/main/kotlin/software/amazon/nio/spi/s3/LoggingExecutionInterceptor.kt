package software.amazon.nio.spi.s3

import org.slf4j.LoggerFactory
import software.amazon.awssdk.core.interceptor.Context
import software.amazon.awssdk.core.interceptor.ExecutionAttributes
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor

internal class LoggingExecutionInterceptor : ExecutionInterceptor {

    private val log = LoggerFactory.getLogger(LoggingExecutionInterceptor::class.java)

    override fun beforeTransmission(
        context: Context.BeforeTransmission,
        executionAttributes: ExecutionAttributes
    ) {
        val request = context.httpRequest()
        log.info(">>> {} {}", request.method(), request.getUri())

        request.headers().forEach { (name, values) ->
            val rendered = if (name.equals("Authorization", ignoreCase = true)) {
                listOf("<redacted>")
            } else {
                values
            }
            log.info(">>> {}: {}", name, rendered.joinToString(","))
        }
    }

    override fun afterTransmission(
        context: Context.AfterTransmission,
        executionAttributes: ExecutionAttributes
    ) {
        val response = context.httpResponse()
        log.info("<<< HTTP {}", response.statusCode())

        response.headers().forEach { (name, values) ->
            log.info("<<< {}: {}", name, values.joinToString(","))
        }
    }

    override fun onExecutionFailure(
        context: Context.FailedExecution,
        executionAttributes: ExecutionAttributes
    ) {
        log.error("<<< request failed: {}", context.exception().message, context.exception())
    }
}