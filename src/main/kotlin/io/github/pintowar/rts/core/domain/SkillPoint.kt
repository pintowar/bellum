package io.github.pintowar.rts.core.domain

import arrow.core.Either
import arrow.core.raise.either

object UnboundedSkillPoint

@JvmInline value class SkillPoint private constructor(private val point: Int) {

    operator fun invoke() = point

    companion object {
        fun valueOf(points: Int): Either<UnboundedSkillPoint, SkillPoint> = either {
            if (points !in 0..<10) raise(UnboundedSkillPoint)
            else SkillPoint(points)
        }
    }
}