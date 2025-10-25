package io.github.pintowar.rts.core.parser.rts

import io.github.pintowar.rts.core.domain.TaskPriority
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class TaskReaderTest :
    FunSpec({

        test("successfully read content") {
            val content =
                """
                id,content,priority,precedes,skill1,skill2,skill3,skill4,skill5,skill6,skill7,skill8,skill9,skill10
                1,Task 1,minor,-1,0,5,5,0,0,1,0,0,1,1
                2,Task 2,major,3,5,0,0,0,0,0,1,0,0,4
                3,Task 3,minor,-1,3,3,0,0,2,3,1,4,0,0
                4,Task 4,minor,-1,0,0,2,0,1,0,0,2,5,0
                5,Task 5,major,2,4,4,0,1,0,1,5,5,1,0
                """.trimIndent()

            val result = RtsTaskReader.readContent(content).getOrThrow()
            result.size shouldBe 5

            val task1 = result.first()
            task1.description shouldBe "Task 1"
            task1.priority shouldBe TaskPriority.MINOR
            task1.dependsOn.shouldBeNull()
            task1.requiredSkills.getValue("skill2")() shouldBe 5
            task1.requiredSkills.getValue("skill4")() shouldBe 0

            val task5 = result.last()
            task5.description shouldBe "Task 5"
            task5.priority shouldBe TaskPriority.MAJOR
            task5.dependsOn?.description shouldBe "Task 2"
            task5.requiredSkills.getValue("skill2")() shouldBe 4
            task5.requiredSkills.getValue("skill4")() shouldBe 1
        }
    })
