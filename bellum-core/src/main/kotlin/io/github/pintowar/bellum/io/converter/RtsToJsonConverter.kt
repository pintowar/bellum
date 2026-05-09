package io.github.pintowar.bellum.io.converter

import io.github.pintowar.bellum.core.io.BaseConverter
import io.github.pintowar.bellum.core.io.ParsedProject
import io.github.pintowar.bellum.io.writer.json.JsonWriter

class RtsToJsonConverter(
    private val writer: JsonWriter = JsonWriter(),
    private val omitSkills: Boolean = false,
) : BaseConverter<ParsedProject, String>() {
    override fun doConvert(input: ParsedProject): String = writer.write(input, omitSkills)
}
