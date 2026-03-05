package io.github.pintowar.bellum.cli

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.testing.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

class ConvertCommandTest :
    FunSpec({
        val command = BellumCommand().subcommands(ConvertCommand())

        val sampleRtsContent =
            """
            id,content,skill1,skill2
            1,Alice,5,3
            2,Bob,2,4
            =================
            id,content,priority,precedes,skill1,skill2
            1,Task 1,minor,-1,3,2
            2,Task 2,major,1,5,0
            3,Task 3,minor,-1,0,3
            """.trimIndent()

        val sampleJsonContent =
            """
            {
                "name": "Test Project",
                "employees": [
                    {"id": 1, "name": "Alice", "skills": [5, 3]},
                    {"id": 2, "name": "Bob", "skills": [2, 4]}
                ],
                "tasks": [
                    {"id": 1, "description": "Task 1", "priority": "minor", "precedes": -1, "requiredSkills": [3, 2]},
                    {"id": 2, "description": "Task 2", "priority": "major", "precedes": 1, "requiredSkills": [5, 0]},
                    {"id": 3, "description": "Task 3", "priority": "minor", "precedes": -1, "requiredSkills": [0, 3]}
                ]
            }
            """.trimIndent()

        test("convert help shows options") {
            val result = command.test("convert --help")
            result.statusCode shouldBe 0
            result.output shouldContain "convert"
        }

        test("convert RTS to JSON auto-detects format") {
            val tempFile = File.createTempFile("test", ".rts")
            tempFile.writeText(sampleRtsContent)
            tempFile.deleteOnExit()

            val result = command.test("convert ${tempFile.absolutePath}")

            result.statusCode shouldBe 0
            result.output shouldContain "\"name\": \"${tempFile.nameWithoutExtension}\""
            result.output shouldContain "\"name\": \"Alice\""
            result.output shouldContain "\"name\": \"Bob\""
        }

        test("convert JSON to RTS auto-detects format") {
            val tempFile = File.createTempFile("test", ".json")
            tempFile.writeText(sampleJsonContent)
            tempFile.deleteOnExit()

            val result = command.test("convert ${tempFile.absolutePath}")

            result.statusCode shouldBe 0
            result.output shouldContain "id,content,skill1,skill2"
            result.output shouldContain "================="
            result.output shouldContain "1,Alice,5,3"
            result.output shouldContain "2,Bob,2,4"
        }

        test("convert with explicit JSON format") {
            val tempFile = File.createTempFile("test", ".txt")
            tempFile.writeText(sampleRtsContent)
            tempFile.deleteOnExit()

            val result = command.test("convert -f json ${tempFile.absolutePath}")

            result.statusCode shouldBe 0
            result.output shouldContain "\"name\": \"${tempFile.nameWithoutExtension}\""
        }

        test("convert with explicit RTS format") {
            val tempFile = File.createTempFile("test", ".txt")
            tempFile.writeText(sampleRtsContent)
            tempFile.deleteOnExit()

            val result = command.test("convert -f rts ${tempFile.absolutePath}")

            result.statusCode shouldBe 0
            result.output shouldContain "================="
        }

        test("convert with output file") {
            val tempInput = File.createTempFile("test", ".rts")
            tempInput.writeText(sampleRtsContent)
            tempInput.deleteOnExit()

            val tempOutput = File.createTempFile("output", ".json")
            tempOutput.delete()
            tempOutput.deleteOnExit()

            val result = command.test("convert -o ${tempOutput.absolutePath} ${tempInput.absolutePath}")

            result.statusCode shouldBe 0
            result.output shouldContain "Converted to json"
            tempOutput.exists() shouldBe true
            val content = tempOutput.readText()
            content shouldContain "\"name\": \"${tempInput.nameWithoutExtension}\""
        }

        test("convert non-existent file fails") {
            val result = command.test("convert /nonexistent/file.rts")

            result.statusCode shouldBe 1
        }

        test("convert invalid file format fails") {
            val tempFile = File.createTempFile("test", ".rts")
            tempFile.writeText("invalid content")
            tempFile.deleteOnExit()

            val result = command.test("convert ${tempFile.absolutePath}")

            result.statusCode shouldBe 1
        }

        test("convert with auto format detects JSON extension") {
            val tempFile = File.createTempFile("test", ".json")
            tempFile.writeText(sampleJsonContent)
            tempFile.deleteOnExit()

            val result = command.test("convert ${tempFile.absolutePath}")

            result.statusCode shouldBe 0
            result.output shouldContain "================="
        }

        test("convert with auto format detects RTS extension") {
            val tempFile = File.createTempFile("test", ".rts")
            tempFile.writeText(sampleRtsContent)
            tempFile.deleteOnExit()

            val result = command.test("convert ${tempFile.absolutePath}")

            result.statusCode shouldBe 0
            result.output shouldContain "\"name\": \"${tempFile.nameWithoutExtension}\""
        }

        test("convert with auto format defaults to JSON for unknown extension") {
            val tempFile = File.createTempFile("test", ".xyz")
            tempFile.writeText(sampleRtsContent)
            tempFile.deleteOnExit()

            val result = command.test("convert ${tempFile.absolutePath}")

            result.statusCode shouldBe 0
            result.output shouldContain "\"name\": \"${tempFile.nameWithoutExtension}\""
        }

        test("convert help shows recalc-matrix option") {
            val result = command.test("convert --help")

            result.statusCode shouldBe 0
            result.output shouldContain "--recalc-matrix"
        }

        test("convert RTS to JSON with recalc-matrix adds estimation matrix") {
            val tempFile = File.createTempFile("test", ".rts")
            tempFile.writeText(sampleRtsContent)
            tempFile.deleteOnExit()

            val result = command.test("convert --recalc-matrix ${tempFile.absolutePath}")

            result.statusCode shouldBe 0
            result.output shouldContain "estimationMatrix"
        }

        test("convert JSON to RTS with recalc-matrix adds estimation matrix") {
            val tempFile = File.createTempFile("test", ".json")
            tempFile.writeText(sampleJsonContent)
            tempFile.deleteOnExit()

            val result = command.test("convert --recalc-matrix ${tempFile.absolutePath}")

            result.statusCode shouldBe 0
            result.output shouldContain "================="
            result.output shouldContain "5,5,85"
            result.output shouldContain "85,85,5"
        }

        test("convert without recalc-matrix does not add matrix when not present") {
            val tempFile = File.createTempFile("test", ".rts")
            tempFile.writeText(sampleRtsContent)
            tempFile.deleteOnExit()

            val result = command.test("convert ${tempFile.absolutePath}")

            result.statusCode shouldBe 0
            result.output shouldNotContain "estimationMatrix"
        }

        test("convert with recalc-matrix -m short flag") {
            val tempFile = File.createTempFile("test", ".rts")
            tempFile.writeText(sampleRtsContent)
            tempFile.deleteOnExit()

            val result = command.test("convert -m ${tempFile.absolutePath}")

            result.statusCode shouldBe 0
            result.output shouldContain "estimationMatrix"
        }
    })
