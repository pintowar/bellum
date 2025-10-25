package io.github.pintowar.rts.core.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class SkillPointTest :
    FunSpec({
        context("create valid point") {
            withData(0, 1, 3, 4, 8, 9) {
                SkillPoint(it) shouldBeSuccess { res ->
                    res() shouldBe it
                }
            }
        }

        context("create invalid point") {
            withData(-3, -2, -1, 10, 15) {
                SkillPoint(it) shouldBeFailure { ex ->
                    ex.shouldBeTypeOf<ValidationException>()
                }
            }
        }
    })
