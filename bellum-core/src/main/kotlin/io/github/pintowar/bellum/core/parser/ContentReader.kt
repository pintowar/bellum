package io.github.pintowar.bellum.core.parser

/**
 * Exception thrown when the file format is invalid or cannot be parsed.
 *
 * @param msg The error message describing the format issue
 */
class InvalidFileFormat(
    msg: String,
) : IllegalArgumentException(msg)

/**
 * Interface for reading and parsing content into domain objects.
 *
 * Implementations should handle parsing errors and return them as [Result.failure]
 * rather than throwing exceptions directly.
 *
 * @param T The type of domain object this reader produces
 */
interface ContentReader<T> {
    /**
     * Parses the given content string into a domain object.
     *
     * @param content The raw content string to parse
     * @return Result containing the parsed domain object or an error
     */
    fun readContent(content: String): Result<T>
}
