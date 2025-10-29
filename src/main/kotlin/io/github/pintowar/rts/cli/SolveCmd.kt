package io.github.pintowar.rts.cli

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import picocli.CommandLine
import java.util.concurrent.Callable

@CommandLine.Command(
    name = "bellum",
    mixinStandardHelpOptions = true,
    version = ["1.0"],
    description = ["A sample native CLI application."],
)
class SolveCmd :
    Callable<Int>,
    KoinComponent {
    private val greetingService: GreetingService by inject()

    @CommandLine.Option(names = ["-n", "--name"], description = ["Your name."], defaultValue = "World")
    private lateinit var name: String

    override fun call(): Int {
        println(greetingService.greet(name))
        return 0
    }
}
