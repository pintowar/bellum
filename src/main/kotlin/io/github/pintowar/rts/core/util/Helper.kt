package io.github.pintowar.rts.core.util

import com.fasterxml.uuid.Generators

object Helper {
    private val generator = Generators.timeBasedEpochGenerator()

    fun uuidV7() = generator.generate()
}


