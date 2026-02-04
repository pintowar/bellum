package io.github.pintowar.bellum.core.functional

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse

/**
 * Provides standard utility functions for working with Arrow-kt types.
 * This module centralizes common Either operations to ensure consistent patterns
 * across the codebase.
 */
object ArrowUtils {
    /**
     * Executes the given [block] and captures any Throwable into an Either.
     * This is the standard way to wrap operations that may throw exceptions.
     *
     * @param block The operation to execute
     * @return Either.Right with the result on success, Either.Left with the exception on failure
     */
    inline fun <A> catching(block: () -> A): Either<Throwable, A> = Either.catch(block)

    /**
     * Transforms the right side of an Either while preserving the left side.
     * Standard map operation for Either.
     *
     * @param transform The transformation to apply to the right value
     * @return A new Either with the transformed value, or the original left value
     */
    inline fun <E, A, B> Either<E, A>.mapRight(transform: (A) -> B): Either<E, B> = map(transform)

    /**
     * Transforms the left side of an Either while preserving the right side.
     * Standard mapLeft operation for Either.
     *
     * @param transform The transformation to apply to the left (error) value
     * @return A new Either with the transformed error, or the original right value
     */
    inline fun <E, A, F> Either<E, A>.mapLeft(transform: (E) -> F): Either<F, A> =
        when (this) {
            is Either.Left -> Either.Left(transform(value))
            is Either.Right -> this
        }

    /**
     * Flattens a nested Either structure.
     * Standard flatten operation for Either.
     *
     * @return The inner Either if outer is Right, otherwise the outer Left
     */
    fun <E, A> Either<E, Either<E, A>>.flatten(): Either<E, A> = flatMap { it }

    /**
     * Chains operations that return Either, with a transformation on success.
     * This is the primary pattern for Either composition.
     *
     * @param transform The next operation to chain
     * @return The result of the chained operation
     */
    inline fun <E, A, B> Either<E, A>.chain(transform: (A) -> Either<E, B>): Either<E, B> = flatMap(transform)

    /**
     * Executes a block on the right value if present.
     * Provides a safe way to perform side effects on successful results.
     *
     * @param block The action to execute on the right value
     * @return This Either for chaining
     */
    inline fun <E, A> Either<E, A>.onSuccess(block: (A) -> Unit): Either<E, A> {
        if (this is Either.Right) {
            block(value)
        }
        return this
    }

    /**
     * Executes a block on the left value if present.
     * Provides a safe way to handle errors with side effects.
     *
     * @param block The action to execute on the left value
     * @return This Either for chaining
     */
    inline fun <E, A> Either<E, A>.onFailure(block: (E) -> Unit): Either<E, A> {
        if (this is Either.Left) {
            block(value)
        }
        return this
    }

    /**
     * Converts an Either to a nullable value.
     * Right becomes the value, Left becomes null.
     *
     * @return The right value or null if left
     */
    fun <E, A> Either<E, A>.toNullable(): A? = getOrNull()

    /**
     * Converts an Either to a nullable value with a default.
     * Right becomes the value, Left becomes the default.
     *
     * @param default The value to return if Left
     * @return The right value or the default
     */
    fun <E, A> Either<E, A>.toNullableOrDefault(default: A): A = getOrElse { default }

    /**
     * Pairs two Eithers together.
     * If both are Right, returns Right with a pair.
     * If either is Left, returns the first Left encountered.
     *
     * @param other The other Either to pair with
     * @return Either.Left of the first failure, or Right with both values
     */
    fun <E, A, B> Either<E, A>.zip(other: Either<E, B>): Either<E, Pair<A, B>> =
        when (this) {
            is Either.Left -> this
            is Either.Right -> {
                when (other) {
                    is Either.Left -> other
                    is Either.Right -> Either.Right(value to other.value)
                }
            }
        }

    /**
     * Combines two Eithers with a transformation function.
     * If both are Right, applies the transformation.
     * If either is Left, returns the first Left encountered.
     *
     * @param other The other Either to combine
     * @param transform The function to combine the two Right values
     * @return Either.Left of the first failure, or Right with the combined result
     */
    inline fun <E, A, B, R> Either<E, A>.zipWith(
        other: Either<E, B>,
        transform: (A, B) -> R,
    ): Either<E, R> =
        when (this) {
            is Either.Left -> this
            is Either.Right -> {
                when (other) {
                    is Either.Left -> other
                    is Either.Right -> Either.Right(transform(value, other.value))
                }
            }
        }

    /**
     * Converts a List<Either<E, A>> to Either<E, List<A>>.
     * Returns the first Left encountered, or all Right values in a list.
     *
     * @return Either.Left of the first failure, or Right with all values
     */
    fun <E, A> List<Either<E, A>>.sequence(): Either<E, List<A>> =
        fold(Either.Right(emptyList())) { acc, either ->
            acc.zipWith(either) { list, value -> list + value }
        }
}
