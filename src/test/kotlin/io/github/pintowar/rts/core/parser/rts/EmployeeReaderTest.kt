package io.github.pintowar.rts.core.parser.rts

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EmployeeReaderTest :
    FunSpec({

        test("successfully read content") {
            val content =
                """
                id,content,skill1,skill2,skill3,skill4,skill5,skill6,skill7,skill8,skill9,skill10
                1,Thiago,0,3,0,1,4,0,3,0,0,0
                2,Bruno,2,1,3,0,0,0,0,0,2,0
                3,Kim,3,2,0,3,0,0,4,4,0,0
                """.trimIndent()

            val result = RtsEmployeeReader.readContent(content).getOrThrow()
            result.size shouldBe 3

            val employee1 = result.first()
            employee1.name shouldBe "Thiago"
            employee1.skills.getValue("skill2")() shouldBe 3
            employee1.skills.getValue("skill4")() shouldBe 1

            val employee3 = result.last()
            employee3.name shouldBe "Kim"
            employee3.skills.getValue("skill2")() shouldBe 2
            employee3.skills.getValue("skill4")() shouldBe 3
        }
    })
