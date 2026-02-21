package io.github.pintowar.bellum.parser.rts

import io.github.pintowar.bellum.core.domain.Project
import io.github.pintowar.bellum.core.parser.ContentReader
import io.github.pintowar.bellum.core.parser.InvalidFileFormat
import java.io.File
import java.net.URI
import kotlin.time.Clock

class RtsProjectReader(
    private val name: String,
) : ContentReader<ParsedProject> {
    companion object {
        private fun content(uri: String) = URI(uri).toURL().readText()

        private fun isSeparatorLine(line: String): Boolean =
            line.isNotBlank() && line.all { it == '=' || it == '-' || it == '_' || it == ' ' }

        /**
         * Reads project content from a file path, trying multiple URI formats.
         * First tries the URI as-is, then file://base/uri, then file://uri.
         * @param base The base directory path
         * @param uri The file path relative to base or absolute
         * @return Result containing the parsed Project or an error
         */
        fun readContentFromPath(
            base: String,
            uri: String,
        ): Result<ParsedProject> {
            val projectName = File(uri).nameWithoutExtension
            return Result
                .success(uri)
                .mapCatching { content(it) }
                .recoverCatching { content("file://$base/$uri") }
                .recoverCatching { content("file://$uri") }
                .mapCatching {
                    RtsProjectReader(projectName).readContent(it).getOrThrow()
                }
        }
    }

    private fun parseMatrix(
        lines: List<String>,
        numEmployees: Int,
        numTasks: Int,
    ): List<List<Long>>? {
        val matrixLines = lines.filter { it.isNotBlank() }
        if (matrixLines.isEmpty()) return null

        val taskIds =
            matrixLines
                .first()
                .split(",")
                .drop(1)
                .map { it.trim() }
        if (taskIds.size != numTasks) {
            throw InvalidFileFormat(
                "Matrix header has ${taskIds.size} task IDs but project has $numTasks tasks.",
            )
        }

        val matrix =
            matrixLines.drop(1).mapIndexed { idx, line ->
                val parts = line.split(",").map { it.trim() }
                if (parts.size != numTasks + 1) {
                    throw InvalidFileFormat(
                        "Matrix row has ${parts.size - 1} values but expected $numTasks.",
                    )
                }
                parts.drop(1).mapIndexed { colIdx, value ->
                    try {
                        value.toLong()
                    } catch (e: NumberFormatException) {
                        throw InvalidFileFormat(
                            "Invalid duration value '$value' at matrix row ${idx + 1}, column ${colIdx + 1}.",
                        )
                    }
                }
            }

        if (matrix.size != numEmployees) {
            throw InvalidFileFormat(
                "Matrix has ${matrix.size} employee rows but project has $numEmployees employees.",
            )
        }

        return matrix
    }

    /**
     * Parses the project content string into a Project domain object.
     * Expects a format with employees above a separator line and tasks below.
     * Optionally, a matrix section can follow tasks (after another separator line).
     * @param content The raw content string to parse
     * @param sep The delimiter used within employee and task sections
     * @return Result containing the parsed Project or an error
     */
    override fun readContent(
        content: String,
        sep: String,
    ): Result<ParsedProject> =
        runCatching {
            val trimmedContent = content.trim()
            if (trimmedContent.isBlank()) {
                throw InvalidFileFormat("Empty project content.")
            }

            val lines = trimmedContent.lines()
            val separatorIndices =
                lines.mapIndexedNotNull { idx, line ->
                    if (isSeparatorLine(line)) idx else null
                }

            if (separatorIndices.isEmpty()) {
                throw InvalidFileFormat("Missing separator line.")
            }

            val firstSepIdx = separatorIndices.first()

            val employeeLines = lines.take(firstSepIdx)
            val afterFirstSep = lines.drop(firstSepIdx + 1)

            val secondSepIdx =
                separatorIndices.getOrNull(1)?.let { secondIdx ->
                    secondIdx - firstSepIdx - 1
                }

            val (taskLines, matrixLines) =
                if (secondSepIdx != null && secondSepIdx > 0) {
                    afterFirstSep.take(secondSepIdx) to afterFirstSep.drop(secondSepIdx + 1)
                } else {
                    afterFirstSep to emptyList()
                }

            val employeeContent = employeeLines.joinToString("\n")
            val taskContent = taskLines.joinToString("\n")

            val employees =
                if (employeeContent.isBlank()) {
                    emptyList()
                } else {
                    RtsEmployeeReader.readContent(employeeContent).getOrThrow()
                }

            val tasks =
                if (taskContent.isBlank()) {
                    emptyList()
                } else {
                    RtsTaskReader.readContent(taskContent).getOrThrow()
                }

            val project = Project(name, Clock.System.now(), employees.toSet(), tasks.toSet()).getOrThrow()

            val estimationMatrix = parseMatrix(matrixLines, employees.size, tasks.size)

            ParsedProject(project, estimationMatrix)
        }
}
