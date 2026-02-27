package io.github.pintowar.bellum.parser

import io.github.pintowar.bellum.core.parser.ContentReader
import io.github.pintowar.bellum.parser.json.JsonProjectReader
import io.github.pintowar.bellum.parser.rts.RtsProjectReader
import java.io.File
import java.net.URI

/**
 * Unified project reader that automatically detects file format based on file extension.
 *
 * This class delegates to the appropriate reader:
 * - [JsonProjectReader] for `.json` files
 * - [RtsProjectReader] for all other file formats (`.rts`, `.txt`, etc.)
 *
 * Use [readContentFromPath] to read a project from a file path with automatic format detection.
 */
class ProjectReader : ContentReader<ParsedProject> {
    /**
     * Parsing content directly is not supported. Use [readContentFromPath] instead.
     */
    override fun readContent(content: String): Result<ParsedProject> = Result.failure(NotImplementedError("Method not implements"))

    companion object {
        /**
         * Reads project content from a file path, automatically detecting the format based on file extension.
         *
         * @param base The base directory path for relative file paths
         * @param uri The file path (relative to base or absolute)
         * @return Result containing the parsed ParsedProject or an error
         */
        private fun content(uri: String) = URI(uri).toURL().readText()

        /**
         * Reads project content from a file path, automatically detecting the format based on file extension.
         *
         * @param base The base directory path for relative file paths
         * @param uri The file path (relative to base or absolute)
         * @return Result containing the parsed ParsedProject or an error
         */
        fun readContentFromPath(
            base: String,
            uri: String,
        ): Result<ParsedProject> {
            val extension = File(uri).extension.lowercase()
            val projectName = File(uri).nameWithoutExtension

            val contentResult =
                Result
                    .success(uri)
                    .mapCatching { content(it) }
                    .recoverCatching { content("file://$base/$uri") }
                    .recoverCatching { content("file://$uri") }

            return if (extension == "json") {
                contentResult.mapCatching {
                    JsonProjectReader(projectName).readContent(it).getOrThrow()
                }
            } else {
                contentResult.mapCatching {
                    RtsProjectReader(projectName).readContent(it).getOrThrow()
                }
            }
        }
    }
}
