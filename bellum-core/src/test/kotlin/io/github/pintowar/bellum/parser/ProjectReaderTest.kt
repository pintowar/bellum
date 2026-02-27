package io.github.pintowar.bellum.parser

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import java.io.File

class ProjectReaderTest :
    FunSpec({

        context("readContentFromPath with real files") {
            val tempDir = File(System.getProperty("java.io.tmpdir"))
            val rtsFile = File(tempDir, "test_project.rts")
            val jsonFile = File(tempDir, "test_project.json")

            afterSpec {
                rtsFile.delete()
                jsonFile.delete()
            }

            test("should parse RTS file correctly") {
                rtsFile.writeText(
                    """
                    id,content,skill1,skill2
                    1,Alice,5,3
                    2,Bob,2,4
                    ========================================
                    id,content,priority,precedes,skill1,skill2
                    1,Task 1,minor,-1,3,2
                    """.trimIndent(),
                )

                val result = ProjectReader.readContentFromPath(tempDir.absolutePath, rtsFile.name)
                result.shouldBeSuccess {
                    it.project.name shouldBe "test_project"
                    it.project.allEmployees().size shouldBe 2
                    it.project.allTasks().size shouldBe 1
                }
            }

            test("should parse JSON file correctly") {
                jsonFile.writeText(
                    """
                    {
                        "name": "JSON Project",
                        "employees": [
                            {"id": 1, "name": "Alice", "skills": [5, 3]},
                            {"id": 2, "name": "Bob", "skills": [2, 4]}
                        ],
                        "tasks": [
                            {"id": 1, "description": "Task 1", "priority": "minor", "precedes": -1, "requiredSkills": [3, 2]}
                        ]
                    }
                    """.trimIndent(),
                )

                val result = ProjectReader.readContentFromPath(tempDir.absolutePath, jsonFile.name)
                result.shouldBeSuccess {
                    it.project.name shouldBe "JSON Project"
                    it.project.allEmployees().size shouldBe 2
                    it.project.allTasks().size shouldBe 1
                }
            }

            test("should fail for non-existent file") {
                val result = ProjectReader.readContentFromPath(tempDir.absolutePath, "nonexistent.rts")
                result.shouldBeFailure()
            }

            test("should fail for malformed JSON content") {
                jsonFile.writeText("{invalid json}")

                val result = ProjectReader.readContentFromPath(tempDir.absolutePath, jsonFile.name)
                result.shouldBeFailure()
            }

            test("should fail for malformed RTS content") {
                rtsFile.writeText("invalid content without separator")

                val result = ProjectReader.readContentFromPath(tempDir.absolutePath, rtsFile.name)
                result.shouldBeFailure()
            }
        }

        context("extension detection") {
            test("should use JsonProjectReader for .json extension") {
                val result = ProjectReader.readContentFromPath("", "project.json")
                result.shouldBeFailure()
            }

            test("should use RtsProjectReader for .rts extension") {
                val result = ProjectReader.readContentFromPath("", "project.rts")
                result.shouldBeFailure()
            }

            test("should use RtsProjectReader for .txt extension") {
                val result = ProjectReader.readContentFromPath("", "project.txt")
                result.shouldBeFailure()
            }

            test("should use JsonProjectReader for uppercase .JSON extension") {
                val result = ProjectReader.readContentFromPath("", "project.JSON")
                result.shouldBeFailure()
            }

            test("should use JsonProjectReader for mixed case .Json extension") {
                val result = ProjectReader.readContentFromPath("", "project.Json")
                result.shouldBeFailure()
            }

            test("should use RtsProjectReader for no extension") {
                val result = ProjectReader.readContentFromPath("", "project")
                result.shouldBeFailure()
            }
        }
    })
