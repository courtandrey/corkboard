package app.corkboard.common

import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler(private val problems: Problems) {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun apiException(e: ApiException): ProblemDetail = problems.detail(e.status, e.code)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalidBody(e: MethodArgumentNotValidException): ProblemDetail =
        problems.detail(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.VALIDATION_FAILED).apply {
            setProperty(
                "fields",
                e.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "invalid") },
            )
        }

    @ExceptionHandler(ConstraintViolationException::class)
    fun invalidParams(e: ConstraintViolationException): ProblemDetail =
        problems.detail(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.VALIDATION_FAILED).apply {
            setProperty(
                "fields",
                e.constraintViolations.associate {
                    it.propertyPath.toString().substringAfterLast('.') to it.message
                },
            )
        }

    @ExceptionHandler(Exception::class)
    fun fallback(e: Exception): ProblemDetail {
        if (e is ErrorResponse) {
            val status = HttpStatus.resolve(e.statusCode.value()) ?: HttpStatus.INTERNAL_SERVER_ERROR
            return problems.detail(status, codeFor(status))
        }
        log.error("Unhandled exception", e)
        return problems.detail(HttpStatus.INTERNAL_SERVER_ERROR, ProblemCode.INTERNAL)
    }

    private fun codeFor(status: HttpStatus): ProblemCode = when {
        status == HttpStatus.NOT_FOUND -> ProblemCode.NOT_FOUND
        status == HttpStatus.UNAUTHORIZED -> ProblemCode.UNAUTHENTICATED
        status == HttpStatus.FORBIDDEN -> ProblemCode.FORBIDDEN
        status.is4xxClientError -> ProblemCode.BAD_REQUEST
        else -> ProblemCode.INTERNAL
    }
}
