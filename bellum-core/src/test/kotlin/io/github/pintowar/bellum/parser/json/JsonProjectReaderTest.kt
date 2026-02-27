package io.github.pintowar.bellum.parser.json

import io.github.pintowar.bellum.core.parser.InvalidFileFormat
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class JsonProjectReaderTest :
    FunSpec({

        test("successfully read content") {
            val content =
                """
                {
                    "name": "Sample Project",
                    "employees": [
                        {"id": 1, "name": "Thiago", "skills": [0, 3]},
                        {"id": 2, "name": "Bruno", "skills": [2, 1]},
                        {"id": 3, "name": "Kim", "skills": [3, 2]}
                    ],
                    "tasks": [
                        {"id": 1, "description": "Task 1", "priority": "minor", "precedes": -1, "requiredSkills": [0, 5]},
                        {"id": 2, "description": "Task 2", "priority": "major", "precedes": 3, "requiredSkills": [5, 0]},
                        {"id": 3, "description": "Task 3", "priority": "minor", "precedes": -1, "requiredSkills": [3, 3]},
                        {"id": 4, "description": "Task 4", "priority": "minor", "precedes": -1, "requiredSkills": [0, 0]},
                        {"id": 5, "description": "Task 5", "priority": "major", "precedes": 2, "requiredSkills": [4, 4]}
                    ]
                }
                """.trimIndent()

            val result = JsonProjectReader("Sample Project").readContent(content).getOrThrow()
            result.project.allEmployees().size shouldBe 3
            result.project.allTasks().size shouldBe 5
            result.project.name shouldBe "Sample Project"
        }

        context("empty content") {
            test("empty content should fail") {
                val result = JsonProjectReader("Test").readContent("")
                result shouldBeFailure { ex ->
                    ex.shouldBeTypeOf<InvalidFileFormat>()
                    ex.message shouldBe "Empty project content."
                }
            }

            test("whitespace only content should fail") {
                val result = JsonProjectReader("Test").readContent("   ")
                result shouldBeFailure { ex ->
                    ex.shouldBeTypeOf<InvalidFileFormat>()
                    ex.message shouldBe "Empty project content."
                }
            }

            test("empty JSON object should succeed with defaults") {
                val result = JsonProjectReader("Test").readContent("{}")
                result.shouldBeSuccess { (project, _) ->
                    project.name shouldBe "Test"
                    project.allEmployees() shouldHaveSize 0
                    project.allTasks() shouldHaveSize 0
                }
            }
        }

        context("malformed JSON") {
            test("invalid JSON should fail") {
                val content = "{invalid json}"
                val result = JsonProjectReader("Test").readContent(content)
                result.shouldBeFailure()
            }

            test("non-object root should fail") {
                val content = "[1,2,3]"
                val result = JsonProjectReader("Test").readContent(content)
                result.shouldBeFailure()
            }
        }

        context("employee section errors") {
            test("malformed employee section should fail") {
                val content =
                    """
                    {
                        "employees": [{"id": 1, "name": null}]
                    }
                    """.trimIndent()
                val result = JsonProjectReader("Test").readContent(content)
                result.shouldBeFailure()
            }

            test("employee section with invalid skill values should fail") {
                val content =
                    """
                    {
                        "employees": [{"id": 1, "name": "Thiago", "skills": ["abc"]}]
                    }
                    """.trimIndent()
                val result = JsonProjectReader("Test").readContent(content)
                result.shouldBeFailure()
            }
        }

        context("task section errors") {
            test("malformed task section should fail") {
                val content =
                    """
                    {
                        "tasks": [{"id": 1, "description": null}]
                    }
                    """.trimIndent()
                val result = JsonProjectReader("Test").readContent(content)
                result.shouldBeFailure()
            }

            test("task section with invalid priority should fail") {
                val content =
                    """
                    {
                        "tasks": [{"id": 1, "description": "Task 1", "priority": "invalid", "precedes": -1}]
                    }
                    """.trimIndent()
                val result = JsonProjectReader("Test").readContent(content)
                result.shouldBeFailure()
            }
        }

        context("edge cases with empty sections") {
            test("empty employee array should succeed") {
                val content =
                    """
                    {
                        "employees": [],
                        "tasks": [{"id": 1, "description": "Task 1", "priority": "minor", "precedes": -1}]
                    }
                    """.trimIndent()

                val result = JsonProjectReader("Test").readContent(content).getOrThrow()
                result.project.allEmployees().size shouldBe 0
                result.project.allTasks().size shouldBe 1
            }

            test("empty task array should succeed") {
                val content =
                    """
                    {
                        "employees": [{"id": 1, "name": "Thiago", "skills": [0]}],
                        "tasks": []
                    }
                    """.trimIndent()

                val result = JsonProjectReader("Test").readContent(content).getOrThrow()
                result.project.allEmployees().size shouldBe 1
                result.project.allTasks().size shouldBe 0
            }
        }

        context("project metadata") {
            test("project should have correct name from JSON") {
                val content =
                    """
                    {
                        "name": "My Test Project",
                        "employees": [{"id": 1, "name": "Thiago", "skills": [0]}],
                        "tasks": [{"id": 1, "description": "Task 1", "priority": "minor", "precedes": -1}]
                    }
                    """.trimIndent()

                val result = JsonProjectReader("Default").readContent(content).getOrThrow()

                result.project.name shouldBe "My Test Project"
            }

            test("default name should be used when not provided") {
                val content =
                    """
                    {
                        "employees": [{"id": 1, "name": "Thiago", "skills": [0]}],
                        "tasks": [{"id": 1, "description": "Task 1", "priority": "minor", "precedes": -1}]
                    }
                    """.trimIndent()

                val result = JsonProjectReader("Default Name").readContent(content).getOrThrow()
                result.project.name shouldBe "Default Name"
            }
        }

        context("estimation matrix") {
            test("valid matrix should be parsed correctly") {
                val content =
                    """
                    {
                        "employees": [
                            {"id": 1, "name": "Alice", "skills": [5, 3]},
                            {"id": 2, "name": "Bob", "skills": [2, 4]}
                        ],
                        "tasks": [
                            {"id": 1, "description": "Task 1", "priority": "minor", "precedes": -1, "requiredSkills": [3, 2]},
                            {"id": 2, "description": "Task 2", "priority": "major", "precedes": -1, "requiredSkills": [1, 1]}
                        ],
                        "estimationMatrix": [[10,20],[30,40]]
                    }
                    """.trimIndent()

                val result = JsonProjectReader("Matrix Project").readContent(content).getOrThrow()
                result.project.allEmployees().size shouldBe 2
                result.project.allTasks().size shouldBe 2
                result.estimationMatrix.shouldNotBeNull()
                val matrix = result.estimationMatrix
                matrix.size shouldBe 2
                matrix[0] shouldBe listOf(10L, 20L)
                matrix[1] shouldBe listOf(30L, 40L)
            }

            test("without matrix should return null") {
                val content =
                    """
                    {
                        "employees": [{"id": 1, "name": "Alice", "skills": [5, 3]}],
                        "tasks": [{"id": 1, "description": "Task 1", "priority": "minor", "precedes": -1}]
                    }
                    """.trimIndent()

                val result = JsonProjectReader("No Matrix").readContent(content).getOrThrow()
                result.estimationMatrix.shouldBeNull()
            }

            test("matrix with wrong number of columns should fail") {
                val content =
                    """
                    {
                        "employees": [
                            {"id": 1, "name": "Alice", "skills": [5, 3]},
                            {"id": 2, "name": "Bob", "skills": [2, 4]}
                        ],
                        "tasks": [
                            {"id": 1, "description": "Task 1", "priority": "minor", "precedes": -1, "requiredSkills": [3, 2]},
                            {"id": 2, "description": "Task 2", "priority": "major", "precedes": -1, "requiredSkills": [1, 1]}
                        ],
                        "estimationMatrix": [[10],[30]]
                    }
                    """.trimIndent()

                val result = JsonProjectReader("Wrong Matrix").readContent(content)
                result.shouldBeFailure()
            }

            test("matrix with wrong number of rows should fail") {
                val content =
                    """
                    {
                        "employees": [
                            {"id": 1, "name": "Alice", "skills": [5, 3]},
                            {"id": 2, "name": "Bob", "skills": [2, 4]}
                        ],
                        "tasks": [
                            {"id": 1, "description": "Task 1", "priority": "minor", "precedes": -1, "requiredSkills": [3, 2]},
                            {"id": 2, "description": "Task 2", "priority": "major", "precedes": -1, "requiredSkills": [1, 1]}
                        ],
                        "estimationMatrix": [[10,20]]
                    }
                    """.trimIndent()

                val result = JsonProjectReader("Wrong Rows").readContent(content)
                result.shouldBeFailure()
            }
        }

        context("complex valid content") {
            test("multiple employees and tasks should work") {
                val content =
                    """
                    {
                        "name": "Complex Project",
                        "employees": [
                            {"id": 1, "name": "Alice", "skills": [5, 3, 0]},
                            {"id": 2, "name": "Bob", "skills": [2, 4, 1]},
                            {"id": 3, "name": "Charlie", "skills": [0, 0, 5]},
                            {"id": 4, "name": "Diana", "skills": [3, 1, 2]}
                        ],
                        "tasks": [
                            {"id": 1, "description": "Backend API", "priority": "major", "precedes": -1, "requiredSkills": [3, 2]},
                            {"id": 2, "description": "Frontend UI", "priority": "minor", "precedes": 1, "requiredSkills": [1, 1]},
                            {"id": 3, "description": "Database Design", "priority": "major", "precedes": -1, "requiredSkills": [2, 0]},
                            {"id": 4, "description": "Testing", "priority": "minor", "precedes": 2, "requiredSkills": [0, 0]},
                            {"id": 5, "description": "Documentation", "priority": "minor", "precedes": 3, "requiredSkills": [0, 0]}
                        ]
                    }
                    """.trimIndent()

                val result = JsonProjectReader("Complex Project").readContent(content).getOrThrow()
                result.project.allEmployees().size shouldBe 4
                result.project.allTasks().size shouldBe 5
                result.project.name shouldBe "Complex Project"

                val task2 = result.project.allTasks().find { it.description == "Frontend UI" }
                task2?.dependsOn?.description shouldBe "Backend API"
            }
        }
    })
