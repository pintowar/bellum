package io.github.pintowar.bellum.parser.converter

interface Converter<I, O> {
    fun convert(input: I): Result<O>
}

abstract class BaseConverter<I, O> : Converter<I, O> {
    abstract fun doConvert(input: I): O

    override fun convert(input: I): Result<O> =
        runCatching {
            doConvert(input)
        }
}
