package io.github.pintowar.bellum.core.io

interface ContentWriter<T> {
    fun write(input: T): String
}
