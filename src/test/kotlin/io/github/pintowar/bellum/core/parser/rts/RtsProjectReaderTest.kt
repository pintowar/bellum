package io.github.pintowar.bellum.core.parser.rts

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RtsProjectReaderTest :
    FunSpec({

        test("successfully read content") {
            val content =
                """
                id,content,skill1,skill2,skill3,skill4,skill5,skill6,skill7,skill8,skill9,skill10
                1,Thiago,0,3,0,1,4,0,3,0,0,0
                2,Bruno,2,1,3,0,0,0,0,0,2,0
                3,Kim,3,2,0,3,0,0,4,4,0,0
                =================================================================================
                id,content,priority,precedes,skill1,skill2,skill3,skill4,skill5,skill6,skill7,skill8,skill9,skill10
                1,Task 1,minor,-1,0,5,5,0,0,1,0,0,1,1
                2,Task 2,major,3,5,0,0,0,0,0,1,0,0,4
                3,Task 3,minor,-1,3,3,0,0,2,3,1,4,0,0
                4,Task 4,minor,-1,0,0,2,0,1,0,0,2,5,0
                5,Task 5,major,2,4,4,0,1,0,1,5,5,1,0
                """.trimIndent()

            val result = RtsProjectReader("Sample Project").readContent(content).getOrThrow()
            result.allEmployees().size shouldBe 3
            result.allTasks().size shouldBe 5
        }
    })
