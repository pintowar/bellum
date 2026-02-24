package io.github.pintowar.bellum.cli

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.testing.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain

class BellumCommandTest :
    FunSpec({
        val command = BellumCommand().subcommands(SolversCommand(), SolveCommand())

        test("check version") {
            val result = command.test("--version")
            result.output shouldContain "bellum version"
        }

        test("check help") {
            val result = command.test("--help")
            result.output shouldContain "Usage: bellum"
            result.output shouldContain "solvers"
            result.output shouldContain "solve"
        }

        test("check solvers help") {
            val result = command.test("solvers --help")
            result.output shouldContain "solvers"
        }

        test("check solve help") {
            val result = command.test("solve --help")
            result.output shouldContain "-l, --limit"
            result.output shouldContain "-o, --output"
            result.output shouldContain "-s, --solver"
        }
    })
