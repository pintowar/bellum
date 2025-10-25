package io.github.pintowar.rts.core.parser

interface ContentReader<T> {
    fun readContent(
        content: String,
        sep: String = ",",
    ): Result<T>
}
