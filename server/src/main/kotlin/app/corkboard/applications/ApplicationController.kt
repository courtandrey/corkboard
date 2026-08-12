package app.corkboard.applications

import app.corkboard.auth.SessionAuthentication
import app.corkboard.common.ApiException
import app.corkboard.common.ProblemCode
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class ApplicationController(private val applications: ApplicationService) {

    @PreAuthorize("hasAuthority('EVENT_APPLY')")
    @PostMapping("/events/{id}/apply")
    fun apply(
        @PathVariable id: UUID,
        @Valid @RequestBody req: ApplyRequest,
        auth: SessionAuthentication,
    ): ResponseEntity<ApplyResponse> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(applications.apply(id, auth.user.userId, req.message))

    @PreAuthorize("hasAuthority('EVENT_APPLY')")
    @PatchMapping("/applications/{id}")
    fun updateStatus(
        @PathVariable id: UUID,
        @RequestBody req: UpdateApplicationRequest,
        auth: SessionAuthentication,
    ): ApplicationResponse = applications.updateStatus(id, auth.user.userId, req.status)

    @PreAuthorize("hasAuthority('EVENT_APPLY')")
    @PostMapping("/applications/{id}/withdraw")
    fun withdraw(@PathVariable id: UUID, auth: SessionAuthentication): ApplicationResponse =
        applications.withdraw(id, auth.user.userId)

    @GetMapping("/me/applications")
    fun myApplications(
        @RequestParam role: String,
        auth: SessionAuthentication,
    ): MyApplicationsResponse {
        val parsed = when (role) {
            "sent" -> ApplicationRole.SENT
            "received" -> ApplicationRole.RECEIVED
            else -> throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCode.VALIDATION_FAILED)
        }
        return applications.myApplications(auth.user.userId, parsed)
    }
}
