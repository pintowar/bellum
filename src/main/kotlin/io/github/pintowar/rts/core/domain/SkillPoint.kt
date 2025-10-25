package io.github.pintowar.rts.core.domain

import io.konform.validation.Validation
import io.konform.validation.Validation.Companion.invoke
import io.konform.validation.constraints.maximum
import io.konform.validation.constraints.minimum
import io.konform.validation.constraints.notBlank

@JvmInline value class SkillPoint private constructor(private val point: Int) {

    operator fun invoke() = point

    companion object {
        private val validator = Validation<SkillPoint> {
            SkillPoint::point {
                minimum(0)
                maximum(9)
            }
        }

        fun valueOf(points: Int): Result<SkillPoint> = runCatching {
            SkillPoint(points).also {
                val res = validator.validate(it)
                if (!res.isValid) throw ValidationException(res.errors)
            }
        }
    }
}