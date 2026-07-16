package app.corkboard.common

import org.springframework.http.HttpStatus

class ApiException(
    val status: HttpStatus,
    val code: ProblemCode,
) : RuntimeException(code.wireValue())
