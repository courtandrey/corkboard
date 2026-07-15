package app.corkboard.meta

import app.corkboard.common.CorkboardProperties
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

data class MetaResponse(
    val types: List<TypeMeta>,
    val limits: Limits,
    val reportThreshold: Int,
    val googleAuth: Boolean,
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
        limits = Limits(),
        reportThreshold = props.reportAutoHideThreshold,
        googleAuth = props.googleAuthEnabled,
    )
}
