package io.github.pintowar.bellum.core.parser

class InvalidFileFormat(
    msg: String,
) : IllegalArgumentException(msg)

interface ContentReader<T> {
    fun readContent(
        content: String,
        sep: String = ",",
    ): Result<T>
}
