package io.github.pintowar.bellum.core.parser

import arrow.core.Either

class InvalidFileFormat(
    msg: String,
) : IllegalArgumentException(msg)

interface ContentReader<T> {
    fun readContent(
        content: String,
        sep: String = ",",
    ): Either<Throwable, T>
}
