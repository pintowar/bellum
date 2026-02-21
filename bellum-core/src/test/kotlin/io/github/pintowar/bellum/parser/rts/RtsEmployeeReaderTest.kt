package io.github.pintowar.bellum.parser.rts

import io.github.pintowar.bellum.core.parser.InvalidFileFormat
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class RtsEmployeeReaderTest :
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

        context("empty content") {
            test("empty content should fail") {
                val result = RtsEmployeeReader.readContent("")
                result shouldBeFailure { ex ->
                    ex.shouldBeTypeOf<InvalidFileFormat>()
                    ex.message shouldBe "Empty employee content."
                }
            }

            test("only header should return empty list") {
                val result = RtsEmployeeReader.readContent("id,content,skill1,skill2")
                result.shouldBeSuccess()
                result.getOrThrow() shouldBe emptyList()
            }
        }

        context("malformed CSV format") {
            test("missing header should fail") {
                val content =
                    """
                    1,Thiago,0,3,0,1
                    2,Bruno,2,1,3,0
                    """.trimIndent()
                val result = RtsEmployeeReader.readContent(content)
                result.shouldBeFailure()
            }

            test("inconsistent column count should fail") {
                val content =
                    """
                    id,content,skill1,skill2
                    1,Thiago,0,3,0
                    2,Bruno,2,1
                    """.trimIndent()
                val result = RtsEmployeeReader.readContent(content)
                result shouldBeFailure { ex ->
                    ex.shouldBeTypeOf<InvalidFileFormat>()
                    ex.message shouldBe
                        "Invalid employee content on line 1: num contents (5) values does not match headers (4)."
                }
            }

            test("missing content field should fail") {
                val content =
                    """
                    id,skill1,skill2
                    1,0,3
                    2,2,1
                    """.trimIndent()
                val result = RtsEmployeeReader.readContent(content)
                result.shouldBeFailure()
            }

            test("empty lines in middle should fail") {
                val content =
                    """
                    id,content,skill1,skill2

                    1,Thiago,0,3
                    2,Bruno,2,1
                    """.trimIndent()
                val result = RtsEmployeeReader.readContent(content)
                result.shouldBeFailure()
            }
        }

        context("invalid skill values") {
            test("skill value too high should fail") {
                val content =
                    """
                    id,content,skill1,skill2
                    1,Thiago,0,10
                    """.trimIndent()
                val result = RtsEmployeeReader.readContent(content)
                result.shouldBeFailure()
            }

            test("skill value negative should fail") {
                val content =
                    """
                    id,content,skill1,skill2
                    1,Thiago,-1,3
                    """.trimIndent()
                val result = RtsEmployeeReader.readContent(content)
                result.shouldBeFailure()
            }

            test("skill value non-numeric should fail") {
                val content =
                    """
                    id,content,skill1,skill2
                    1,Thiago,abc,3
                    """.trimIndent()
                val result = RtsEmployeeReader.readContent(content)
                result.shouldBeFailure()
            }

            test("skill value decimal should fail") {
                val content =
                    """
                    id,content,skill1,skill2
                    1,Thiago,2.5,3
                    """.trimIndent()
                val result = RtsEmployeeReader.readContent(content)
                result.shouldBeFailure()
            }
        }

        context("invalid employee name") {
            test("empty name should fail") {
                val content =
                    """
                    id,content,skill1,skill2
                    1,,0,3
                    """.trimIndent()
                val result = RtsEmployeeReader.readContent(content)
                result.shouldBeFailure()
            }

            test("whitespace only name should fail") {
                val content =
                    """
                    id,content,skill1,skill2
                    1,  ,0,3
                    """.trimIndent()
                val result = RtsEmployeeReader.readContent(content)
                result.shouldBeFailure()
            }
        }

        context("edge cases") {
            test("single employee with no skills should succeed") {
                val content =
                    """
                    id,content
                    1,Thiago
                    """.trimIndent()
                val result = RtsEmployeeReader.readContent(content)
                result.shouldBeSuccess()
                result.getOrThrow().size shouldBe 1
                result.getOrThrow().first().name shouldBe "Thiago"
                result.getOrThrow().first().skills shouldBe emptyMap()
            }

            test("many skills columns should succeed") {
                val content =
                    """
                    id,content,skill1,skill2,skill3,skill4,skill5,skill6,skill7,skill8,skill9,skill10,skill11,skill12
                    1,Thiago,0,3,0,1,4,0,3,0,0,0,1,2
                    """.trimIndent()
                val result = RtsEmployeeReader.readContent(content)
                result.shouldBeSuccess()
                result
                    .getOrThrow()
                    .first()
                    .skills.size shouldBe 12
            }

            test("missing skill columns should succeed") {
                val content =
                    """
                    id,content,skill1
                    1,Thiago,5
                    """.trimIndent()
                val result = RtsEmployeeReader.readContent(content)
                result.shouldBeSuccess()
                result
                    .getOrThrow()
                    .first()
                    .skills.size shouldBe 1
            }

            test("skill column with different order should succeed") {
                val content =
                    """
                    id,content,skill3,skill1,skill2
                    1,Thiago,1,0,3
                    """.trimIndent()
                val result = RtsEmployeeReader.readContent(content)
                result.shouldBeSuccess()
                result
                    .getOrThrow()
                    .first()
                    .skills
                    .getValue("skill1")() shouldBe 0
                result
                    .getOrThrow()
                    .first()
                    .skills
                    .getValue("skill2")() shouldBe 3
                result
                    .getOrThrow()
                    .first()
                    .skills
                    .getValue("skill3")() shouldBe 1
            }
        }

        context("different separators") {
            test("semicolon separator should work") {
                val content =
                    """
                    id;content;skill1;skill2
                    1;Thiago;0;3
                    2;Bruno;2;1
                    """.trimIndent()
                val result = RtsEmployeeReader.readContent(content, ";")
                result.shouldBeSuccess()
                result.getOrThrow().size shouldBe 2
            }

            test("tab separator should work") {
                val content =
                    """
                    id	content	skill1	skill2
                    1	Thiago	0	3
                    2	Bruno	2	1
                    """.trimIndent()
                val result = RtsEmployeeReader.readContent(content, "\t")
                result.shouldBeSuccess()
                result.getOrThrow().size shouldBe 2
            }

            test("custom separator should work") {
                val content =
                    """
                    id|content|skill1|skill2
                    1|Thiago|0|3
                    2|Bruno|2|1
                    """.trimIndent()
                val result = RtsEmployeeReader.readContent(content, "|")
                result.shouldBeSuccess()
                result.getOrThrow().size shouldBe 2
            }
        }

        context("CSV with quotes and special characters") {
            xtest("quotes in content field should work") {
                val content =
                    """
                    id,content,skill1,skill2
                    1,"John Doe",0,3
                    2,"Jane, Smith",2,1
                    """.trimIndent()
                val result = RtsEmployeeReader.readContent(content)
                result.shouldBeSuccess()
                result.getOrThrow().first().name shouldBe "John Doe"
                result.getOrThrow().last().name shouldBe "Jane, Smith"
            }

            test("special characters in name should work") {
                val content =
                    """
                    id,content,skill1,skill2
                    1,Mário José,0,3
                    2,Jean-Luc,2,1
                    """.trimIndent()
                val result = RtsEmployeeReader.readContent(content)
                result.shouldBeSuccess()
                result.getOrThrow().first().name shouldBe "Mário José"
                result.getOrThrow().last().name shouldBe "Jean-Luc"
            }
        }
    })
