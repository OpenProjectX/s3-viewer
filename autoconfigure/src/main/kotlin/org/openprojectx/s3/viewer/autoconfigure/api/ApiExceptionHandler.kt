package org.openprojectx.s3.viewer.autoconfigure.api

import org.openprojectx.s3.viewer.autoconfigure.model.ErrorResponse
import org.openprojectx.s3.viewer.core.S3ViewerException
import org.openprojectx.s3.viewer.core.S3ViewerReadOnlyException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(S3ViewerReadOnlyException::class)
    fun handleReadOnlyException(exception: S3ViewerReadOnlyException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse().message(exception.message ?: "S3 Viewer is configured for read-only access"))

    @ExceptionHandler(S3ViewerException::class)
    fun handleS3ViewerException(exception: S3ViewerException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse().message(exception.message ?: "Unknown S3 viewer error"))
}
