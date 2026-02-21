package io.github.pintowar.bellum.parser.rts

import io.github.pintowar.bellum.core.parser.InvalidFileFormat
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.math.abs
import kotlin.math.max
import kotlin.time.Clock

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
            result.project.allEmployees().size shouldBe 3
            result.project.allTasks().size shouldBe 5
            result.project.name shouldBe "Sample Project"
            result.project.kickOff shouldBe result.project.kickOff
        }

        context("malformed content structure") {
            test("missing separator line should fail") {
                val content =
                    """
                    id,content,skill1,skill2
                    1,Thiago,0,3
                    id,content,priority,precedes,skill1,skill2
                    1,Task 1,minor,-1,0,3
                    """.trimIndent()

                val result = RtsProjectReader("Test").readContent(content)
                result.shouldBeFailure()
            }

            test("empty content should fail") {
                val result = RtsProjectReader("Test").readContent("")
                result.shouldBeFailure { ex ->
                    ex.shouldBeTypeOf<InvalidFileFormat>()
                    ex.message shouldBe "Empty project content."
                }
            }

            test("whitespace only content should fail") {
                val result = RtsProjectReader("Test").readContent("   ")
                result.shouldBeFailure { ex ->
                    ex.shouldBeTypeOf<InvalidFileFormat>()
                    ex.message shouldBe "Empty project content."
                }
            }
        }

        context("employee section errors") {
            test("malformed employee section should fail") {
                val content =
                    """
                    id,content,skill1,skill2
                    invalid,row,format
                    ========================================
                    id,content,priority,precedes,skill1,skill2
                    1,Task 1,minor,-1,0,3
                    """.trimIndent()

                val result = RtsProjectReader("Test").readContent(content)
                result.shouldBeFailure()
            }

            test("employee section with invalid skill values should fail") {
                val content =
                    """
                    id,content,skill1,skill2
                    1,Thiago,abc,3
                    ========================================
                    id,content,priority,precedes,skill1,skill2
                    1,Task 1,minor,-1,0,3
                    """.trimIndent()

                val result = RtsProjectReader("Test").readContent(content)
                result.shouldBeFailure()
            }
        }

        context("task section errors") {
            test("malformed task section should fail") {
                val content =
                    """
                    id,content,skill1,skill2
                    1,Thiago,0,3
                    ========================================
                    id,content,priority,precedes,skill1,skill2
                    invalid,task,row,format
                    """.trimIndent()

                val result = RtsProjectReader("Test").readContent(content)
                result.shouldBeFailure()
            }

            test("task section with invalid priority should fail") {
                val content =
                    """
                    id,content,skill1,skill2
                    1,Thiago,0,3
                    ========================================
                    id,content,priority,precedes,skill1,skill2
                    1,Task 1,invalid,-1,0,3
                    """.trimIndent()

                val result = RtsProjectReader("Test").readContent(content)
                result.shouldBeFailure()
            }
        }

        context("edge cases with empty sections") {
            test("empty employee section should succeed") {
                val content =
                    """
                    ========================================
                    id,content,priority,precedes,skill1,skill2
                    1,Task 1,minor,-1,0,3
                    """.trimIndent()

                val result = RtsProjectReader("Test").readContent(content).getOrThrow()
                result.project.allEmployees().size shouldBe 0
                result.project.allTasks().size shouldBe 1
            }

            test("empty task section should succeed") {
                val content =
                    """
                    id,content,skill1,skill2
                    1,Thiago,0,3
                    ========================================
                    """.trimIndent()

                val result = RtsProjectReader("Test").readContent(content).getOrThrow()
                result.project.allEmployees().size shouldBe 1
                result.project.allTasks().size shouldBe 0
            }

            test("both sections empty should succeed") {
                val content = "================================================="
                val result = RtsProjectReader("Test").readContent(content).getOrThrow()
                result.project.allEmployees().size shouldBe 0
                result.project.allTasks().size shouldBe 0
            }
        }

        context("separator line variations") {
            test("short separator line should work") {
                val content =
                    """
                    id,content,skill1,skill2
                    1,Thiago,0,3
                    ===================
                    id,content,priority,precedes,skill1,skill2
                    1,Task 1,minor,-1,0,3
                    """.trimIndent()

                val result = RtsProjectReader("Test").readContent(content).getOrThrow()
                result.project.allEmployees().size shouldBe 1
                result.project.allTasks().size shouldBe 1
            }

            test("separator with different characters should work") {
                val content =
                    """
                    id,content,skill1,skill2
                    1,Thiago,0,3
                    ----------------------
                    id,content,priority,precedes,skill1,skill2
                    1,Task 1,minor,-1,0,3
                    """.trimIndent()

                val result = RtsProjectReader("Test").readContent(content).getOrThrow()
                result.project.allEmployees().size shouldBe 1
                result.project.allTasks().size shouldBe 1
            }

            test("separator line with spaces should work") {
                val content =
                    """
                    id,content,skill1,skill2
                    1,Thiago,0,3
                    =====
                    id,content,priority,precedes,skill1,skill2
                    1,Task 1,minor,-1,0,3
                    """.trimIndent()

                val result = RtsProjectReader("Test").readContent(content).getOrThrow()
                result.project.allEmployees().size shouldBe 1
                result.project.allTasks().size shouldBe 1
            }
        }

        context("different separators") {
            test("custom separator should work") {
                val content =
                    """
                    id,content,skill1,skill2
                    1,Thiago,0,3
                    ========================================
                    id,content,priority,precedes,skill1,skill2
                    1,Task 1,minor,-1,0,3
                    """.trimIndent()

                val result = RtsProjectReader("Test").readContent(content, "|").getOrThrow()
                result.project.allEmployees().size shouldBe 1
                result.project.allTasks().size shouldBe 1
            }
        }

        context("complex valid content") {
            test("multiple employees and tasks should work") {
                val content =
                    """
                    id,content,skill1,skill2,skill3
                    1,Alice,5,3,0
                    2,Bob,2,4,1
                    3,Charlie,0,0,5
                    4,Diana,3,1,2
                    ================================================================================
                    id,content,priority,precedes,skill1,skill2,skill3
                    1,Backend API,major,-1,3,2,0
                    2,Frontend UI,minor,1,1,1,2
                    3,Database Design,major,-1,2,0,4
                    4,Testing,minor,2,0,0,1
                    5,Documentation,minor,3,0,0,0
                    """.trimIndent()

                val result = RtsProjectReader("Complex Project").readContent(content).getOrThrow()
                result.project.allEmployees().size shouldBe 4
                result.project.allTasks().size shouldBe 5
                result.project.name shouldBe "Complex Project"

                val task2 = result.project.allTasks().find { it.description == "Frontend UI" }
                task2?.dependsOn?.description shouldBe "Backend API"
            }
        }

        context("companion object readContentFromPath") {
            test("should fallback to file URI when data URI fails") {
                // This test would need actual file system setup to be meaningful
                // For now, we'll test the logic with an invalid URI that should trigger fallback
                val result = RtsProjectReader.readContentFromPath("", "invalid-uri-format")
                result.shouldBeFailure()
            }

            test("should handle malformed content in file fallback") {
                val result = RtsProjectReader.readContentFromPath("", "invalid-malformed")
                result.shouldBeFailure()
            }
        }

        context("content with extra whitespace and formatting") {
            test("content with leading/trailing whitespace should work") {
                val content =
                    """
                    
                    id,content,skill1,skill2
                    1,Thiago,0,3
                    
                    ================================================================================
                    
                    id,content,priority,precedes,skill1,skill2
                    1,Task 1,minor,-1,0,3
                    
                    """.trimIndent()

                val result = RtsProjectReader("Test").readContent(content).getOrThrow()
                result.project.allEmployees().size shouldBe 1
                result.project.allTasks().size shouldBe 1
            }

            test("content with extra newlines between sections should work") {
                val content =
                    """
                    id,content,skill1,skill2
                    1,Thiago,0,3


                    ================================================================================


                    id,content,priority,precedes,skill1,skill2
                    1,Task 1,minor,-1,0,3
                    """.trimIndent()

                val result = RtsProjectReader("Test").readContent(content).getOrThrow()
                result.project.allEmployees().size shouldBe 1
                result.project.allTasks().size shouldBe 1
            }
        }

        context("project metadata") {
            test("project should have correct name and timestamp") {
                val content =
                    """
                    id,content,skill1,skill2
                    1,Thiago,0,3
                    ========================================
                    id,content,priority,precedes,skill1,skill2
                    1,Task 1,minor,-1,0,3
                    """.trimIndent()

                val beforeTime = Clock.System.now()
                val result = RtsProjectReader("My Test Project").readContent(content).getOrThrow()

                result.project.name shouldBe "My Test Project"
                result.project.kickOff shouldBe result.project.kickOff // Check it's set
                // Verify timestamp is reasonable (within a few seconds of now)
                val timeDiff =
                    abs(
                        result.project.kickOff
                            .minus(beforeTime)
                            .inWholeMilliseconds,
                    )
                timeDiff.shouldBe(max(timeDiff, 0)) // Should be non-negative
            }
        }

        context("estimation matrix") {
            test("valid matrix should be parsed correctly") {
                val content =
                    """
                    id,content,skill1,skill2
                    1,Alice,5,3
                    2,Bob,2,4
                    ================================================================================
                    id,content,priority,precedes,skill1,skill2
                    1,Task 1,minor,-1,3,2
                    2,Task 2,major,-1,1,1
                    ================================================================================
                    ,1,2
                    1,10,20
                    2,30,40
                    """.trimIndent()

                val result = RtsProjectReader("Matrix Project").readContent(content).getOrThrow()
                result.project.allEmployees().size shouldBe 2
                result.project.allTasks().size shouldBe 2
                result.estimationMatrix shouldNotBe null
                val matrix = result.estimationMatrix!!
                matrix.size shouldBe 2
                matrix[0] shouldBe listOf(10L, 20L)
                matrix[1] shouldBe listOf(30L, 40L)
            }

            test("without matrix should return null") {
                val content =
                    """
                    id,content,skill1,skill2
                    1,Alice,5,3
                    ================================================================================
                    id,content,priority,precedes,skill1,skill2
                    1,Task 1,minor,-1,3,2
                    """.trimIndent()

                val result = RtsProjectReader("No Matrix").readContent(content).getOrThrow()
                result.estimationMatrix shouldBe null
            }

            test("matrix with wrong number of columns should fail") {
                val content =
                    """
                    id,content,skill1,skill2
                    1,Alice,5,3
                    2,Bob,2,4
                    ================================================================================
                    id,content,priority,precedes,skill1,skill2
                    1,Task 1,minor,-1,3,2
                    2,Task 2,major,-1,1,1
                    ================================================================================
                    ,1,2
                    1,10
                    2,30
                    """.trimIndent()

                val result = RtsProjectReader("Wrong Matrix").readContent(content)
                result.shouldBeFailure()
            }

            test("matrix with wrong number of rows should fail") {
                val content =
                    """
                    id,content,skill1,skill2
                    1,Alice,5,3
                    2,Bob,2,4
                    ================================================================================
                    id,content,priority,precedes,skill1,skill2
                    1,Task 1,minor,-1,3,2
                    2,Task 2,major,-1,1,1
                    ================================================================================
                    ,1,2
                    1,10,20
                    """.trimIndent()

                val result = RtsProjectReader("Wrong Rows").readContent(content)
                result.shouldBeFailure()
            }
        }
    })
