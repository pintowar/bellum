package io.github.pintowar.rts.core.parser.rts

import io.github.pintowar.rts.core.domain.TaskPriority
import io.github.pintowar.rts.core.parser.InvalidFileFormat
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class RtsTaskReaderTest :
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

        context("empty content") {
            test("empty content should fail") {
                val result = RtsTaskReader.readContent("")
                result shouldBeFailure { ex ->
                    ex.shouldBeTypeOf<InvalidFileFormat>()
                    ex.message shouldBe "Empty task content."
                }
            }

            test("only header should return empty list") {
                val result = RtsTaskReader.readContent("id,content,priority,precedes,skill1,skill2")
                result.shouldBeSuccess()
                result.getOrThrow() shouldBe emptyList()
            }
        }

        context("malformed CSV format") {
            test("missing header should fail") {
                val content =
                    """
                    1,Task 1,minor,-1,0,3
                    2,Task 2,major,3,2,1
                    """.trimIndent()
                val result = RtsTaskReader.readContent(content)
                result.shouldBeFailure()
            }

            test("inconsistent column count should fail") {
                val content =
                    """
                    id,content,priority,precedes,skill1,skill2
                    1,Task 1,minor,-1,0,3,0
                    2,Task 2,major,3,2,1
                    """.trimIndent()
                val result = RtsTaskReader.readContent(content)
                result shouldBeFailure { ex ->
                    ex.shouldBeTypeOf<InvalidFileFormat>()
                    ex.message shouldBe
                        "Invalid task content on line 1: num contents (7) values does not match headers (6)."
                }
            }

            test("missing content field should fail") {
                val content =
                    """
                    id,priority,precedes,skill1,skill2
                    1,minor,-1,0,3
                    2,major,3,2,1
                    """.trimIndent()
                val result = RtsTaskReader.readContent(content)
                result.shouldBeFailure()
            }

            test("empty lines in middle should fail") {
                val content =
                    """
                    id,content,priority,precedes,skill1,skill2

                    1,Task 1,minor,-1,0,3
                    2,Task 2,major,3,2,1
                    """.trimIndent()
                val result = RtsTaskReader.readContent(content)
                result.shouldBeFailure()
            }
        }

        context("invalid skill values") {
            test("skill value too high should fail") {
                val content =
                    """
                    id,content,priority,precedes,skill1,skill2
                    1,Task 1,minor,-1,0,10
                    """.trimIndent()
                val result = RtsTaskReader.readContent(content)
                result.shouldBeFailure()
            }

            test("skill value negative should fail") {
                val content =
                    """
                    id,content,priority,precedes,skill1,skill2
                    1,Task 1,minor,-1,-1,3
                    """.trimIndent()
                val result = RtsTaskReader.readContent(content)
                result.shouldBeFailure()
            }

            test("skill value non-numeric should fail") {
                val content =
                    """
                    id,content,priority,precedes,skill1,skill2
                    1,Task 1,minor,-1,abc,3
                    """.trimIndent()
                val result = RtsTaskReader.readContent(content)
                result.shouldBeFailure()
            }

            test("skill value decimal should fail") {
                val content =
                    """
                    id,content,priority,precedes,skill1,skill2
                    1,Task 1,minor,-1,2.5,3
                    """.trimIndent()
                val result = RtsTaskReader.readContent(content)
                result.shouldBeFailure()
            }
        }

        context("invalid task description") {
            test("empty description should fail") {
                val content =
                    """
                    id,content,priority,precedes,skill1,skill2
                    1,,minor,-1,0,3
                    """.trimIndent()
                val result = RtsTaskReader.readContent(content)
                result.shouldBeFailure()
            }

            test("whitespace only description should fail") {
                val content =
                    """
                    id,content,priority,precedes,skill1,skill2
                    1,  ,minor,-1,0,3
                    """.trimIndent()
                val result = RtsTaskReader.readContent(content)
                result.shouldBeFailure()
            }
        }

        context("invalid priority") {
            test("invalid priority string should fail") {
                val content =
                    """
                    id,content,priority,precedes,skill1,skill2
                    1,Task 1,invalid,-1,0,3
                    """.trimIndent()
                val result = RtsTaskReader.readContent(content)
                result.shouldBeFailure()
            }

            test("empty priority should fail") {
                val content =
                    """
                    id,content,priority,precedes,skill1,skill2
                    1,Task 1,, -1,0,3
                    """.trimIndent()
                val result = RtsTaskReader.readContent(content)
                result.shouldBeFailure()
            }
        }

        context("invalid precedence") {
            test("invalid precedence string should fail") {
                val content =
                    """
                    id,content,priority,precedes,skill1,skill2
                    1,Task 1,minor,-1,0,3
                    2,Task 2,major,3,2,1
                    """.trimIndent()
                val result = RtsTaskReader.readContent(content)
                result shouldBeFailure { ex ->
                    ex.shouldBeTypeOf<InvalidFileFormat>()
                    ex.message shouldBe "Precedence (3) of task (2) not found."
                }
            }
        }

        context("edge cases") {
            test("single task with no skills should succeed") {
                val content =
                    """
                    id,content,priority,precedes
                    1,Task 1,minor,-1
                    """.trimIndent()
                val result = RtsTaskReader.readContent(content)
                result.shouldBeSuccess()
                result.getOrThrow().size shouldBe 1
                result.getOrThrow().first().description shouldBe "Task 1"
                result.getOrThrow().first().requiredSkills shouldBe emptyMap()
            }

            test("many skills columns should succeed") {
                val content =
                    """
                    id,content,priority,precedes,skill1,skill2,skill3,skill4,skill5,skill6,skill7,skill8,skill9,skill10,skill11,skill12
                    1,Task 1,minor,-1,0,3,0,1,4,0,3,0,0,0,1,2
                    """.trimIndent()
                val result = RtsTaskReader.readContent(content)
                result.shouldBeSuccess()
                result
                    .getOrThrow()
                    .first()
                    .requiredSkills.size shouldBe 12
            }

            test("missing skill columns should succeed") {
                val content =
                    """
                    id,content,priority,precedes,skill1
                    1,Task 1,minor,-1,5
                    """.trimIndent()
                val result = RtsTaskReader.readContent(content)
                result.shouldBeSuccess()
                result
                    .getOrThrow()
                    .first()
                    .requiredSkills.size shouldBe 1
            }

            test("skill column with different order should succeed") {
                val content =
                    """
                    id,content,priority,precedes,skill3,skill1,skill2
                    1,Task 1,minor,-1,1,0,3
                    """.trimIndent()
                val result = RtsTaskReader.readContent(content)
                result.shouldBeSuccess()
                result
                    .getOrThrow()
                    .first()
                    .requiredSkills
                    .getValue("skill1")() shouldBe 0
                result
                    .getOrThrow()
                    .first()
                    .requiredSkills
                    .getValue("skill2")() shouldBe 3
                result
                    .getOrThrow()
                    .first()
                    .requiredSkills
                    .getValue("skill3")() shouldBe 1
            }

            test("task with valid precedes should succeed") {
                val content =
                    """
                    id,content,priority,precedes
                    1,Task 1,minor,-1
                    2,Task 2,major,1
                    """.trimIndent()
                val result = RtsTaskReader.readContent(content)
                result.shouldBeSuccess()
                result.getOrThrow().size shouldBe 2
                result
                    .getOrThrow()
                    .last()
                    .dependsOn
                    ?.description shouldBe "Task 1"
            }

            test("task with invalid precedes should fail") {
                val content =
                    """
                    id,content,priority,precedes
                    1,Task 1,minor,-1
                    2,Task 2,major,3
                    """.trimIndent()
                val result = RtsTaskReader.readContent(content)
                result.shouldBeFailure()
            }
        }

        context("different separators") {
            test("semicolon separator should work") {
                val content =
                    """
                    id;content;priority;precedes;skill1;skill2
                    1;Task 1;minor;-1;0;3
                    2;Task 2;major;1;2;1
                    """.trimIndent()
                val result = RtsTaskReader.readContent(content, ";")
                result.shouldBeSuccess()
                result.getOrThrow().size shouldBe 2
            }

            test("tab separator should work") {
                val content =
                    """
                    id	content	priority	precedes	skill1	skill2
                    1	Task 1	minor	-1	0	3
                    2	Task 2	major	1	2	1
                    """.trimIndent()
                val result = RtsTaskReader.readContent(content, "	")
                result.shouldBeSuccess()
                result.getOrThrow().size shouldBe 2
            }

            test("custom separator should work") {
                val content =
                    """
                    id|content|priority|precedes|skill1|skill2
                    1|Task 1|minor|-1|0|3
                    2|Task 2|major|1|2|1
                    """.trimIndent()
                val result = RtsTaskReader.readContent(content, "|")
                result.shouldBeSuccess()
                result.getOrThrow().size shouldBe 2
            }
        }

        context("CSV with quotes and special characters") {
            xtest("quotes in content field should work") {
                val content =
                    """
                    id,content,priority,precedes,skill1,skill2
                    1,"Task One",minor,-1,0,3
                    2,"Task, Two",major,1,2,1
                    """.trimIndent()
                val result = RtsTaskReader.readContent(content)
                result.shouldBeSuccess()
                result.getOrThrow().first().description shouldBe "Task One"
                result.getOrThrow().last().description shouldBe "Task, Two"
            }

            test("special characters in description should work") {
                val content =
                    """
                    id,content,priority,precedes,skill1,skill2
                    1,Tarefa Um,minor,-1,0,3
                    2,Tâche Deux,major,1,2,1
                    """.trimIndent()
                val result = RtsTaskReader.readContent(content)
                result.shouldBeSuccess()
                result.getOrThrow().first().description shouldBe "Tarefa Um"
                result.getOrThrow().last().description shouldBe "Tâche Deux"
            }
        }
    })
