package io.github.pintowar.bellum.cli

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import picocli.CommandLine
import java.io.PrintWriter
import java.io.StringWriter

class BellumCmdTest :
    FunSpec({
        val app = BellumCmd()
        val cmd = CommandLine(app)

        test("check version") {
            val sw = StringWriter()
            cmd.out = PrintWriter(sw)

            val exitCode: Int = cmd.execute("-V")
            exitCode shouldBe 0
            sw.toString().trim() shouldStartWith "bellum version is "
        }

        xtest("check helper") {
            val sw = StringWriter()
            cmd.out = PrintWriter(sw)

            val exitCode: Int = cmd.execute("-h")
            exitCode shouldBe 0
            sw.toString().trim() shouldStartWith "bellum version is "
        }
    })
