package io.github.pintowar.bellum.core.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.konform.validation.Validation
import io.konform.validation.constraints.maximum
import io.konform.validation.constraints.minimum

@JvmInline value class SkillPoint private constructor(
    private val value: Int,
) {
    operator fun invoke() = value

    companion object {
        private val validator =
            Validation<SkillPoint> {
                SkillPoint::value {
                    minimum(0)
                    maximum(9)
                }
            }

        operator fun invoke(points: Int): Either<ValidationException, SkillPoint> {
            val sp = SkillPoint(points)
            val res = validator.validate(sp)
            return if (res.isValid) {
                sp.right()
            } else {
                ValidationException(res.errors.toValidationErrorDetails()).left()
            }
        }
    }
}
