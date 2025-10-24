package io.github.pintowar.rts.core.domain

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class SkillPointTest : FunSpec({
    context("create valid point") {
        withData(0, 1,3, 4, 8, 9) {
            SkillPoint.Companion.valueOf(it).onRight { p ->
                p() shouldBe it
            }
        }
    }

    context("create invalid point") {
        withData(-3, -2, -1, 10, 15) {
            SkillPoint.Companion.valueOf(it) shouldBeLeft UnboundedSkillPoint
        }
    }
})