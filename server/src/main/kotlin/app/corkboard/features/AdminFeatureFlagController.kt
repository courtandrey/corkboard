package app.corkboard.features

import app.corkboard.auth.SessionAuthentication
import app.corkboard.common.ApiException
import app.corkboard.common.ProblemCode
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.Locale
import org.springframework.context.MessageSource
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class FeatureFlagItem(
    val key: String,
    val label: String,
    val description: String,
    val enabled: Boolean,
    val updatedAt: Instant?,
    val updatedBy: String?,
)

data class FeatureFlagListResponse(val items: List<FeatureFlagItem>)

data class UpdateFeatureFlagRequest(
    @field:NotNull
    val enabled: Boolean,
)

@RestController
@RequestMapping("/api/v1/admin/features")
class AdminFeatureFlagController(
    private val flags: FeatureFlagService,
    private val messages: MessageSource,
) {

    @PreAuthorize("hasAuthority('FEATURE_FLAG_MANAGE')")
    @GetMapping
    fun list(): FeatureFlagListResponse = FeatureFlagListResponse(flags.changes().map(::toItem))

    @PreAuthorize("hasAuthority('FEATURE_FLAG_MANAGE')")
    @PatchMapping("/{key}")
    fun update(
        @PathVariable key: String,
        @Valid @RequestBody req: UpdateFeatureFlagRequest,
        auth: SessionAuthentication,
    ): FeatureFlagItem {
        val flag = FeatureFlag.of(key) ?: throw ApiException(HttpStatus.NOT_FOUND, ProblemCode.NOT_FOUND)
        val state = flags.set(flag, req.enabled, auth.user.userId)
        return toItem(FeatureFlagChange(flag, state.enabled, state.updatedAt, auth.user.displayName))
    }

    private fun toItem(change: FeatureFlagChange) = FeatureFlagItem(
        key = change.flag.name,
        label = messages.getMessage(change.flag.labelKey, null, Locale.ENGLISH),
        description = messages.getMessage(change.flag.descriptionKey, null, Locale.ENGLISH),
        enabled = change.enabled,
        updatedAt = change.updatedAt,
        updatedBy = change.updatedByName,
    )
}
