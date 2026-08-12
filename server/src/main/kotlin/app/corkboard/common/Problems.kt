package app.corkboard.common

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletResponse
import java.util.Locale
import org.springframework.context.MessageSource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.stereotype.Component

@Component
class Problems(
    private val messages: MessageSource,
    private val objectMapper: ObjectMapper,
) {

    fun detail(status: HttpStatus, code: ProblemCode): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            status,
            messages.getMessage("problem.${code.wireValue()}", null, code.wireValue(), Locale.ENGLISH),
        ).apply {
            title = status.reasonPhrase
            setProperty("code", code)
        }

    fun write(response: HttpServletResponse, status: HttpStatus, code: ProblemCode) {
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        objectMapper.writeValue(response.writer, detail(status, code))
    }
}
