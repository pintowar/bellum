package io.github.pintowar.bellum.io.converter

import io.github.pintowar.bellum.core.io.BaseConverter
import io.github.pintowar.bellum.io.reader.json.JsonProjectReader
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeTypeOf

class ConverterTest :
    FunSpec({
        val rtsToJsonConverter = RtsToJsonConverter()
        val jsonToRtsConverter = JsonToRtsConverter()

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

        context("RtsToJsonConverter") {
            test("successfully convert RTS to JSON") {
                val parsedProject =
                    io.github.pintowar.bellum.io.reader.rts
                        .RtsProjectReader("Test")
                        .readContent(sampleRtsContent)
                        .getOrThrow()

                val result = rtsToJsonConverter.convert(parsedProject)

                result.shouldBeSuccess()
                val json = result.getOrThrow()
                json.shouldContain("Test")
                json.shouldContain("Alice")
                json.shouldContain("Bob")
                json.shouldContain("Task 1")
                json.shouldContain("Task 2")
                json.shouldContain("Task 3")
            }

            test("convert preserves project name") {
                val parsedProject =
                    io.github.pintowar.bellum.io.reader.rts
                        .RtsProjectReader(
                            "My Project",
                        ).readContent(sampleRtsContent)
                        .getOrThrow()

                val result = rtsToJsonConverter.convert(parsedProject)

                result.shouldBeSuccess()
                val json = result.getOrThrow()
                json.shouldContain("My Project")
            }

            test("convert preserves employee skills") {
                val parsedProject =
                    io.github.pintowar.bellum.io.reader.rts
                        .RtsProjectReader("Test")
                        .readContent(sampleRtsContent)
                        .getOrThrow()

                val result = rtsToJsonConverter.convert(parsedProject)

                result.shouldBeSuccess()
                val json = result.getOrThrow()
                json.shouldContain("5")
                json.shouldContain("3")
                json.shouldContain("2")
                json.shouldContain("4")
            }

            test("convert preserves task dependencies") {
                val parsedProject =
                    io.github.pintowar.bellum.io.reader.rts
                        .RtsProjectReader("Test")
                        .readContent(sampleRtsContent)
                        .getOrThrow()

                val result = rtsToJsonConverter.convert(parsedProject)

                result.shouldBeSuccess()
                val json = result.getOrThrow()
                json.shouldContain("1")
                json.shouldContain("-1")
            }

            test("convert handles empty project") {
                val emptyRts =
                    """
                    id,content
                    =================
                    id,content,priority,precedes
                    """.trimIndent()
                val parsedProject =
                    io.github.pintowar.bellum.io.reader.rts
                        .RtsProjectReader("Empty")
                        .readContent(emptyRts)
                        .getOrThrow()

                val result = rtsToJsonConverter.convert(parsedProject)

                result.shouldBeSuccess()
                val json = result.getOrThrow()
                json.shouldContain("Empty")
            }

            test("convert handles estimation matrix") {
                val rtsWithMatrix =
                    """
                    id,content,skill1
                    1,Alice,5
                    2,Bob,3
                    =================
                    id,content,priority,precedes,skill1
                    1,Task 1,minor,-1,3
                    2,Task 2,major,-1,2
                    =================
                    10,20
                    30,40
                    """.trimIndent()
                val parsedProject =
                    io.github.pintowar.bellum.io.reader.rts
                        .RtsProjectReader("Test")
                        .readContent(rtsWithMatrix)
                        .getOrThrow()

                val result = rtsToJsonConverter.convert(parsedProject)

                result.shouldBeSuccess()
                val json = result.getOrThrow()
                json.shouldContain("estimationMatrix")
            }
        }

        context("JsonToRtsConverter") {
            test("successfully convert ParsedProject to RTS") {
                val parsedProject = JsonProjectReader("Test").readContent(sampleJsonContent).getOrThrow()

                val result = jsonToRtsConverter.convert(parsedProject)

                result.shouldBeSuccess()
                val rts = result.getOrThrow()
                rts.shouldContain("id,content,skill1,skill2")
                rts.shouldContain("1,Alice,5,3")
                rts.shouldContain("2,Bob,2,4")
                rts.shouldContain("id,content,priority,precedes,skill1,skill2")
                rts.shouldContain("=================")
            }

            test("convert preserves task priorities") {
                val parsedProject = JsonProjectReader("Test").readContent(sampleJsonContent).getOrThrow()

                val result = jsonToRtsConverter.convert(parsedProject)

                result.shouldBeSuccess()
                val rts = result.getOrThrow()
                rts.shouldContain("Task 1,minor,-1")
                rts.shouldContain("Task 2,major,1")
                rts.shouldContain("Task 3,minor,-1")
            }

            test("convert preserves task dependencies") {
                val parsedProject = JsonProjectReader("Test").readContent(sampleJsonContent).getOrThrow()

                val result = jsonToRtsConverter.convert(parsedProject)

                result.shouldBeSuccess()
                val rts = result.getOrThrow()
                val lines = rts.lines()
                val task2Line = lines.find { it.contains("Task 2") }
                task2Line.shouldContain(",1,")
            }

            test("convert handles empty project") {
                val emptyJson = """{"name": "Empty", "employees": [], "tasks": []}"""
                val parsedProject = JsonProjectReader("Empty").readContent(emptyJson).getOrThrow()

                val result = jsonToRtsConverter.convert(parsedProject)

                result.shouldBeSuccess()
                val rts = result.getOrThrow()
                rts.shouldContain("id,content")
                rts.shouldContain("=================")
                rts.shouldContain("id,content,priority,precedes")
            }

            test("convert handles estimation matrix") {
                val jsonWithMatrix =
                    """
                    {
                        "name": "Matrix Test",
                        "employees": [
                            {"id": 1, "name": "Alice", "skills": [5]},
                            {"id": 2, "name": "Bob", "skills": [3]}
                        ],
                        "tasks": [
                            {"id": 1, "description": "Task 1", "priority": "minor", "precedes": -1, "requiredSkills": [3]},
                            {"id": 2, "description": "Task 2", "priority": "major", "precedes": -1, "requiredSkills": [2]}
                        ],
                        "estimationMatrix": [[10,20],[30,40]]
                    }
                    """.trimIndent()
                val parsedProject = JsonProjectReader("Matrix Test").readContent(jsonWithMatrix).getOrThrow()

                val result = jsonToRtsConverter.convert(parsedProject)

                result.shouldBeSuccess()
                val rts = result.getOrThrow()
                rts.shouldContain("10,20")
                rts.shouldContain("30,40")
            }
        }

        context("roundtrip conversion") {
            test("RTS to JSON to RTS preserves structure") {
                val parsedProject =
                    io.github.pintowar.bellum.io.reader.rts
                        .RtsProjectReader("Test")
                        .readContent(sampleRtsContent)
                        .getOrThrow()

                val jsonResult = rtsToJsonConverter.convert(parsedProject).getOrThrow()
                val jsonProject = JsonProjectReader("Test").readContent(jsonResult).getOrThrow()
                val rtsResult = jsonToRtsConverter.convert(jsonProject).getOrThrow()

                rtsResult.shouldContain("id,content,skill1,skill2")
                rtsResult.shouldContain("1,Alice,5,3")
                rtsResult.shouldContain("2,Bob,2,4")
                rtsResult.shouldContain("=================")
                rtsResult.shouldContain("Task 1,minor,-1")
                rtsResult.shouldContain("Task 2,major,1")
            }

            test("JSON to RTS to JSON preserves structure") {
                val parsedProject = JsonProjectReader("Test").readContent(sampleJsonContent).getOrThrow()

                val rtsResult = jsonToRtsConverter.convert(parsedProject).getOrThrow()
                val reParsed =
                    io.github.pintowar.bellum.io.reader.rts
                        .RtsProjectReader("Test")
                        .readContent(rtsResult)
                        .getOrThrow()
                val jsonResult = rtsToJsonConverter.convert(reParsed).getOrThrow()

                jsonResult.shouldContain("Test")
                jsonResult.shouldContain("Alice")
                jsonResult.shouldContain("Bob")
            }
        }

        context("Converter interface") {
            test("BaseConverter returns success for valid input") {
                val converter =
                    object : BaseConverter<String, String>() {
                        override fun doConvert(input: String): String = input.uppercase()
                    }

                val result = converter.convert("hello")
                result.shouldBeSuccess()
                result.getOrThrow() shouldBe "HELLO"
            }

            test("BaseConverter returns failure for exception") {
                val converter =
                    object : BaseConverter<String, String>() {
                        override fun doConvert(input: String): String = throw IllegalArgumentException("test error")
                    }

                val result = converter.convert("hello")
                result.shouldBeFailure { ex ->
                    ex.shouldBeTypeOf<IllegalArgumentException>()
                    ex.message shouldBe "test error"
                }
            }
        }
    })
