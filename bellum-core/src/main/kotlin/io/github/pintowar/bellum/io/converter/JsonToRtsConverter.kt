package io.github.pintowar.bellum.io.converter

import io.github.pintowar.bellum.core.io.BaseConverter
import io.github.pintowar.bellum.core.io.ParsedProject
import io.github.pintowar.bellum.io.writer.rts.RtsWriter

class JsonToRtsConverter(
    private val writer: RtsWriter = RtsWriter(),
) : BaseConverter<ParsedProject, String>() {
    override fun doConvert(input: ParsedProject): String = writer.write(input)
}
