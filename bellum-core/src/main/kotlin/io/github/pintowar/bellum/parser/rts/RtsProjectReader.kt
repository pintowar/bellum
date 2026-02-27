package io.github.pintowar.bellum.parser.rts

import io.github.pintowar.bellum.core.domain.Project
import io.github.pintowar.bellum.core.parser.ContentReader
import io.github.pintowar.bellum.core.parser.InvalidFileFormat
import io.github.pintowar.bellum.parser.ParsedProject
import kotlin.time.Clock

/**
 * Parser for RTS (Resource Task Scheduling) formatted project files.
 *
 * RTS format uses a text-based structure with:
 * - Employee section above the first separator line
 * - Task section below the first separator line
 * - Optional estimation matrix below the second separator line
 *
 * Example:
 * ```
 * id,name,skill1,skill2
 * 1,Alice,5,3
 * =================
 * id,description,priority,precedes,skill1,skill2
 * 1,Task 1,minor,-1,3,2
 * ```
 *
 * @property name The project name
 * @property sep The delimiter used to separate values in each line (default: ",")
 */
class RtsProjectReader(
    private val name: String,
    private val sep: String = ",",
) : ContentReader<ParsedProject> {
    companion object {
        /**
         * Determines if a line is a separator line (consists only of =, -, _, or space characters).
         */
        private fun isSeparatorLine(line: String): Boolean =
            line.isNotBlank() && line.all { it == '=' || it == '-' || it == '_' || it == ' ' }

        /**
         * Convenience method to parse RTS content directly.
         *
         * @param content The raw RTS content string
         * @param name The project name
         * @return Result containing the parsed ParsedProject or an error
         */
        fun readContent(
            content: String,
            sep: String = ",",
            name: String = "",
        ): Result<ParsedProject> = RtsProjectReader(name, sep).readContent(content)
    }

    /**
     * Parses the project content string into a Project domain object.
     * Expects a format with employees above a separator line and tasks below.
     * Optionally, a matrix section can follow tasks (after another separator line).
     * @param content The raw content string to parse
     * @return Result containing the parsed Project or an error
     */
    override fun readContent(content: String): Result<ParsedProject> =
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
                    RtsEmployeeReader(sep).readContent(employeeContent).getOrThrow()
                }

            val tasks =
                if (taskContent.isBlank()) {
                    emptyList()
                } else {
                    RtsTaskReader(sep).readContent(taskContent).getOrThrow()
                }

            val project = Project(name, Clock.System.now(), employees.toSet(), tasks.toSet()).getOrThrow()

            val estimationMatrixContent = matrixLines.joinToString("\n")
            val estimationMatrix =
                RtsMatrixReader(sep)
                    .readContent(estimationMatrixContent)
                    .mapCatching {
                        if (it.isNotEmpty()) {
                            RtsMatrixReader(sep).validateMatrix(it, employees.size, tasks.size).getOrThrow()
                        } else {
                            null
                        }
                    }.getOrThrow()

            ParsedProject(project, estimationMatrix)
        }
}
