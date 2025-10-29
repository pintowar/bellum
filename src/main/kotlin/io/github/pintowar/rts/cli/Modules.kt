package io.github.pintowar.rts.cli

import org.koin.dsl.module

// Define a service
interface GreetingService {
    fun greet(name: String): String
}

class GreetingServiceImpl : GreetingService {
    override fun greet(name: String): String = "Hello, $name!"
}

// Define a Koin module
val appModule =
    module {
        single<GreetingService> { GreetingServiceImpl() }
    }
