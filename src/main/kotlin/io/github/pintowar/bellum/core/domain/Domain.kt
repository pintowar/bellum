package io.github.pintowar.bellum.core.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.konform.validation.Validation
import io.konform.validation.ValidationError
import io.konform.validation.ValidationResult as KValidationResult

data class ValidationErrorDetail(
    val dataPath: String,
    val message: String,
)

class ValidationException(
    val errors: List<ValidationErrorDetail>,
) : Throwable()

fun List<ValidationError>.toValidationErrorDetails(): List<ValidationErrorDetail> = map { ValidationErrorDetail(it.dataPath, it.message) }

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<ValidationErrorDetail>,
)

fun KValidationResult<*>.toDomain(): ValidationResult = ValidationResult(isValid, errors.toValidationErrorDetails())

fun <T> T.validateAndWrap(validator: Validation<T>): Either<ValidationException, T> =
    validator.validate(this).let { res ->
        if (res.isValid) {
            this.right()
        } else {
            ValidationException(res.errors.toValidationErrorDetails()).left()
        }
    }

fun List<AssignedTask>.hasOverlappingIntervals(): Boolean {
    // An empty list or a list with a single interval cannot have overlaps.
    if (this.size <= 1) {
        return false
    }

    // Sort the intervals based on their start time.
    val sortedIntervals = this.sortedBy { it.startAt }

    // Iterate through the sorted intervals, starting from the second one.
    for (i in 1 until sortedIntervals.size) {
        val currentInterval = sortedIntervals[i]
        val previousInterval = sortedIntervals[i - 1]

        // If the start of the current interval is before the end of the previous one,
        // there is an overlap.
        if (currentInterval.overlaps(previousInterval)) {
            return true
        }
    }

    // If the loop completes without finding any overlaps, return false.
    return false
}
