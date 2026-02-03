package io.github.pintowar.bellum.core.domain

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class SkillPointTest :
    FunSpec({
        context("create valid point") {
            withData(0, 1, 3, 4, 8, 9) {
                val res = SkillPoint(it).shouldBeRight()
                res() shouldBe it
            }
        }

        context("create invalid point") {
            withData(-3, -2, -1, 10, 15) {
                val ex = SkillPoint(it).shouldBeLeft()
                ex.shouldBeTypeOf<ValidationException>()
            }
        }
    })
