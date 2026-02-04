package io.github.pintowar.bellum.core.domain

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

        operator fun invoke(points: Int): Result<SkillPoint> =
            runCatching {
                SkillPoint(points).also {
                    val res = validator.validate(it)
                    if (!res.isValid) throw ValidationException(res.errors.toValidationErrorDetails())
                }
            }
    }
}
