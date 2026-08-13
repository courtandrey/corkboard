package app.corkboard.moderation

import app.corkboard.auth.RoleCatalog
import app.corkboard.auth.SessionAuthentication
import app.corkboard.auth.UserRoleService
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.util.UUID
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class RoleGrantRequest(
    @field:NotBlank
    val role: String,
)

data class RoleCatalogResponse(val roles: List<String>)

@RestController
@RequestMapping("/api/v1/admin")
class AdminController(
    private val admin: AdminService,
    private val userRoles: UserRoleService,
    private val catalog: RoleCatalog,
) {

    @PreAuthorize("hasAuthority('REPORT_QUEUE_VIEW')")
    @GetMapping("/reports")
    fun reports(
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) limit: Int,
    ): ReportQueueResponse = admin.reportQueue(limit)

    @PreAuthorize("hasAuthority('EVENT_TAKE_DOWN_ANY')")
    @PostMapping("/events/{id}/takedown")
    fun takeDown(@PathVariable id: UUID, auth: SessionAuthentication): ResponseEntity<Void> {
        admin.takeDown(id, auth.user.userId)
        return ResponseEntity.noContent().build()
    }

    @PreAuthorize("hasAuthority('EVENT_TAKE_DOWN_ANY')")
    @PostMapping("/events/{id}/approve")
    fun approve(@PathVariable id: UUID, auth: SessionAuthentication): ResponseEntity<Void> {
        admin.approve(id, auth.user.userId)
        return ResponseEntity.noContent().build()
    }

    @PreAuthorize("hasAuthority('EVENT_TAKE_DOWN_ANY')")
    @PostMapping("/events/{id}/restore")
    fun restore(@PathVariable id: UUID, auth: SessionAuthentication): ResponseEntity<Void> {
        admin.restore(id, auth.user.userId)
        return ResponseEntity.noContent().build()
    }

    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @GetMapping("/roles")
    fun roles(): RoleCatalogResponse = RoleCatalogResponse(catalog.roleKeys().sorted())

    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @PostMapping("/users/{userId}/roles")
    fun grant(
        @PathVariable userId: UUID,
        @Valid @RequestBody req: RoleGrantRequest,
        auth: SessionAuthentication,
    ): ResponseEntity<Void> {
        userRoles.grant(userId, req.role, auth.user.userId)
        return ResponseEntity.noContent().build()
    }

    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @DeleteMapping("/users/{userId}/roles/{role}")
    fun revoke(@PathVariable userId: UUID, @PathVariable role: String): ResponseEntity<Void> {
        userRoles.revoke(userId, role)
        return ResponseEntity.noContent().build()
    }
}
