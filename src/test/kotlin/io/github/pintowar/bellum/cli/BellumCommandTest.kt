package io.github.pintowar.bellum.cli

import com.github.ajalt.clikt.testing.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain

class BellumCommandTest :
    FunSpec({
        val command = BellumCommand()

        test("check version") {
            val result = command.test("--version")
            result.output shouldContain "bellum version"
        }

        test("check help") {
            val result = command.test("--help")
            result.output shouldContain "Usage: bellum [<options>] <path>"
        }
    })
