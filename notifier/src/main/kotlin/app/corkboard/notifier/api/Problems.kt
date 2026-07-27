package app.corkboard.notifier.api

import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.stereotype.Component

enum class ProblemCode(@get:JsonValue val wire: String, val detail: String) {
    UNAUTHENTICATED("unauthenticated", "A valid X-Api-Key header is required."),
    VALIDATION_FAILED("validation_failed", "Some fields need attention."),
    DELIVERY_FAILED("delivery_failed", "The message could not be handed to the mail server."),
    NOT_FOUND("not_found", "There’s nothing here."),
    INTERNAL("internal", "Something went wrong on our side."),
}

class ApiException(
    val status: HttpStatus,
    val code: ProblemCode,
    cause: Throwable? = null,
    val fields: Map<String, String>? = null,
) : RuntimeException(code.detail, cause)

@Component
class Problems(private val objectMapper: ObjectMapper) {

    fun detail(status: HttpStatus, code: ProblemCode, fields: Map<String, String>? = null): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, code.detail).apply {
            title = status.reasonPhrase
            setProperty("code", code)
            fields?.let { setProperty("fields", it) }
        }

    fun write(response: HttpServletResponse, status: HttpStatus, code: ProblemCode) {
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        objectMapper.writeValue(response.writer, detail(status, code))
    }
}
