package org.openprojectx.s3.viewer.app.api

import org.openprojectx.s3.viewer.app.model.ErrorResponse
import org.openprojectx.s3.viewer.core.S3ViewerException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(S3ViewerException::class)
    fun handleS3ViewerException(exception: S3ViewerException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse().message(exception.message ?: "Unknown S3 viewer error"))
}
