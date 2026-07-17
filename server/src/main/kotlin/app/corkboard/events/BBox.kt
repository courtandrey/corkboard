package app.corkboard.events

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [BBoxValidator::class])
annotation class BBox(
    val message: String = "{validation.bbox}",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

data class Bounds(val west: Double, val south: Double, val east: Double, val north: Double) {
    companion object {
        fun parse(bbox: String): Bounds? {
            val parts = bbox.split(',').map { it.trim().toDoubleOrNull() ?: return null }
            if (parts.size != 4) return null
            val (west, south, east, north) = parts
            val lngOk = west in -180.0..180.0 && east in -180.0..180.0
            val latOk = south in -85.0..85.0 && north in -85.0..85.0 && south < north
            return if (lngOk && latOk) Bounds(west, south, east, north) else null
        }
    }
}

class BBoxValidator : ConstraintValidator<BBox, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean =
        value == null || Bounds.parse(value) != null
}
