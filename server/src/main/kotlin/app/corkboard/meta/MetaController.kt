package app.corkboard.meta

import app.corkboard.common.CorkboardProperties
import app.corkboard.scopes.ScopeKind
import java.util.Locale
import org.springframework.context.MessageSource
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class TypeMeta(
    val key: String,
    val label: String,
    val color: String,
    val applyableDefault: Boolean,
)

data class ScopeMeta(
    val key: String,
    val label: String,
    val types: List<String>,
)

data class MetaResponse(
    val types: List<TypeMeta>,
    val scopes: List<ScopeMeta>,
    val limits: Limits,
    val reportThreshold: Int,
    val googleAuth: Boolean,
    val placeSearch: Boolean,
)

@RestController
@RequestMapping("/api/v1/meta")
class MetaController(
    private val props: CorkboardProperties,
    private val messages: MessageSource,
) {

    @GetMapping
    fun meta(): MetaResponse = MetaResponse(
        types = EventType.entries.map {
            TypeMeta(it.key, messages.getMessage(it.labelKey, null, Locale.ENGLISH), it.color, it.applyableDefault)
        },
        scopes = ScopeKind.entries.map {
            ScopeMeta(it.key, messages.getMessage(it.labelKey, null, Locale.ENGLISH), it.types.map(EventType::key))
        },
        limits = Limits(),
        reportThreshold = props.reportAutoHideThreshold,
        googleAuth = props.googleAuthEnabled,
        placeSearch = props.geocoder.enabled,
    )
}
