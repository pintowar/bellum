package io.github.pintowar.bellum.parser.rts

import io.github.pintowar.bellum.core.parser.ContentReader
import io.github.pintowar.bellum.core.parser.InvalidFileFormat

object RtsMatrixReader : ContentReader<List<List<Long>>> {
    override fun readContent(
        content: String,
        sep: String,
    ): Result<List<List<Long>>> =
        runCatching {
            if (content.isBlank()) return@runCatching emptyList()
            val lines = content.trim().lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) return@runCatching emptyList()

            lines.drop(1).mapIndexed { idx, line ->
                val parts = line.split(sep).map { it.trim() }
                parts.drop(1).mapIndexed { colIdx, value ->
                    value.toLongOrNull() ?: throw InvalidFileFormat(
                        "Invalid duration value '$value' at matrix row ${idx + 1}, column ${colIdx + 1}.",
                    )
                }
            }
        }

    fun validateMatrix(
        matrix: List<List<Long>>,
        employeesSize: Int,
        tasksSize: Int,
    ): Result<List<List<Long>>> {
        if (matrix.size != employeesSize) {
            return Result.failure(
                InvalidFileFormat(
                    "Matrix has ${matrix.size} employee rows but project has $employeesSize employees.",
                ),
            )
        }
        matrix.firstOrNull()?.let { row ->
            if (row.size != tasksSize) {
                return Result.failure(
                    InvalidFileFormat(
                        "Matrix row has ${row.size} values but project has $tasksSize tasks.",
                    ),
                )
            }
        }
        return Result.success(matrix)
    }
}
