package io.github.pintowar.rts.cli

import org.koin.core.Koin
import picocli.CommandLine

class KoinFactory(
    private val koin: Koin,
) : CommandLine.IFactory {
    override fun <K : Any> create(cls: Class<K>): K = koin.get(cls::class, null, null)
}
