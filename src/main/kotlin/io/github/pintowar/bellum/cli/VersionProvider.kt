package io.github.pintowar.bellum.cli

import picocli.CommandLine
import java.util.Properties

class VersionProvider : CommandLine.IVersionProvider {
    override fun getVersion(): Array<String> {
        val props =
            Thread.currentThread().contextClassLoader.let { cl ->
                Properties().apply { load(cl.getResourceAsStream("application.properties")) }
            }

        return arrayOf("bellum version is ${props["application.version"]}".trim())
    }
}
