package io.github.pintowar.bellum.parser.rts

import io.github.pintowar.bellum.core.parser.InvalidFileFormat
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class RtsMatrixReaderTest :
    FunSpec({

        test("successfully read matrix content") {
            val content =
                """
                ,task1,task2,task3
                emp1,10,20,30
                emp2,15,25,35
                """.trimIndent()

            val result = RtsMatrixReader.readContent(content).getOrThrow()
            result.size shouldBe 2

            result[0] shouldBe listOf(10L, 20L, 30L)
            result[1] shouldBe listOf(15L, 25L, 35L)
        }

        context("empty content") {
            test("blank content should return empty list") {
                val result = RtsMatrixReader.readContent("")
                result.shouldBeSuccess()
                result.getOrThrow() shouldBe emptyList()
            }

            test("whitespace only should return empty list") {
                val result = RtsMatrixReader.readContent("   ")
                result.shouldBeSuccess()
                result.getOrThrow() shouldBe emptyList()
            }

            test("only header should return empty list") {
                val result = RtsMatrixReader.readContent(",task1,task2")
                result.shouldBeSuccess()
                result.getOrThrow() shouldBe emptyList()
            }
        }

        context("malformed CSV format") {
            test("invalid number format should fail") {
                val content =
                    """
                    ,task1,task2
                    emp1,10,abc
                    """.trimIndent()
                val result = RtsMatrixReader.readContent(content)
                result shouldBeFailure { ex ->
                    ex.shouldBeTypeOf<InvalidFileFormat>()
                    ex.message shouldBe "Invalid duration value 'abc' at matrix row 1, column 2."
                }
            }

            test("decimal number should fail") {
                val content =
                    """
                    ,task1,task2
                    emp1,10.5,20
                    """.trimIndent()
                val result = RtsMatrixReader.readContent(content)
                result shouldBeFailure { ex ->
                    ex.shouldBeTypeOf<InvalidFileFormat>()
                    ex.message shouldBe "Invalid duration value '10.5' at matrix row 1, column 1."
                }
            }

            test("negative number should be parsed") {
                val content =
                    """
                    ,task1,task2
                    emp1,-10,20
                    """.trimIndent()
                val result = RtsMatrixReader.readContent(content)
                result.shouldBeSuccess()
                result.getOrThrow()[0] shouldBe listOf(-10L, 20L)
            }
        }

        context("different separators") {
            test("semicolon separator should work") {
                val content =
                    """
                    ;task1;task2
                    emp1;10;20
                    emp2;15;25
                    """.trimIndent()
                val result = RtsMatrixReader.readContent(content, ";")
                result.shouldBeSuccess()
                result.getOrThrow().size shouldBe 2
                result.getOrThrow()[0] shouldBe listOf(10L, 20L)
            }

            test("tab separator should work") {
                val content =
                    """
                    	task1	task2
                    emp1	10	20
                    emp2	15	25
                    """.trimIndent()
                val result = RtsMatrixReader.readContent(content, "\t")
                result.shouldBeSuccess()
                result.getOrThrow().size shouldBe 2
            }
        }

        context("validateMatrix") {
            test("valid matrix should succeed") {
                val matrix = listOf(listOf(10L, 20L), listOf(15L, 25L))
                val result = RtsMatrixReader.validateMatrix(matrix, 2, 2)
                result.shouldBeSuccess()
            }

            test("wrong employee count should fail") {
                val matrix = listOf(listOf(10L, 20L))
                val result = RtsMatrixReader.validateMatrix(matrix, 2, 2)
                result shouldBeFailure { ex ->
                    ex.shouldBeTypeOf<InvalidFileFormat>()
                    ex.message shouldBe "Matrix has 1 employee rows but project has 2 employees."
                }
            }

            test("wrong task count should fail") {
                val matrix = listOf(listOf(10L, 20L, 30L))
                val result = RtsMatrixReader.validateMatrix(matrix, 1, 2)
                result shouldBeFailure { ex ->
                    ex.shouldBeTypeOf<InvalidFileFormat>()
                    ex.message shouldBe "Matrix row has 3 values but project has 2 tasks."
                }
            }

            test("empty matrix should succeed with zero counts") {
                val matrix = emptyList<List<Long>>()
                val result = RtsMatrixReader.validateMatrix(matrix, 0, 0)
                result.shouldBeSuccess()
            }
        }

        context("edge cases") {
            test("single row matrix should work") {
                val content =
                    """
                    ,task1
                    emp1,10
                    """.trimIndent()
                val result = RtsMatrixReader.readContent(content)
                result.shouldBeSuccess()
                result.getOrThrow().size shouldBe 1
                result.getOrThrow()[0] shouldBe listOf(10L)
            }

            test("large numbers should work") {
                val content =
                    """
                    ,task1
                    emp1,9999999999999
                    """.trimIndent()
                val result = RtsMatrixReader.readContent(content)
                result.shouldBeSuccess()
                result.getOrThrow()[0][0] shouldBe 9999999999999L
            }

            test("empty lines should be filtered") {
                val content =
                    """
                    ,task1,task2

                    emp1,10,20
                    """.trimIndent()
                val result = RtsMatrixReader.readContent(content)
                result.shouldBeSuccess()
                result.getOrThrow().size shouldBe 1
            }
        }
    })
