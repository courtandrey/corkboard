package app.corkboard.notifier.api

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler(private val problems: Problems) {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun apiException(e: ApiException): ProblemDetail = problems.detail(e.status, e.code, e.fields)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalidBody(e: MethodArgumentNotValidException): ProblemDetail =
        problems.detail(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ProblemCode.VALIDATION_FAILED,
            e.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "invalid") },
        )

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadableBody(): ProblemDetail =
        problems.detail(HttpStatus.BAD_REQUEST, ProblemCode.VALIDATION_FAILED)

    @ExceptionHandler(Exception::class)
    fun unexpected(e: Exception): ProblemDetail {
        log.error("Unhandled exception", e)
        return problems.detail(HttpStatus.INTERNAL_SERVER_ERROR, ProblemCode.INTERNAL)
    }
}
