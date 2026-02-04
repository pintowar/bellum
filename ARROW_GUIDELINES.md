# Arrow-kt Guidelines for Bellum Project

This document outlines the best practices for using Arrow-kt in the Bellum scheduling project.

## Overview

Arrow-kt is a library for typed functional programming in Kotlin. This project uses Arrow Core for functional error handling with `Either`.

## Core Concepts

### Either Type

`Either<E, A>` represents a value that can be one of two types:
- `Either.Left<E>` represents a failure or error (conventionally the "bad" case)
- `Either.Right<A>` represents a success (conventionally the "good" case)

```kotlin
// Example: Validation that can fail
fun createUser(name: String): Either<ValidationException, User> {
    return if (name.isBlank()) {
        ValidationException("Name cannot be blank").left()
    } else {
        User(name).right()
    }
}
```

## Best Practices

### 1. Use Either for All Error-Prone Operations

Wrap operations that can fail in Either rather than throwing exceptions:

```kotlin
// GOOD: Returns Either
fun parseFile(path: String): Either<IOException, Content> {
    return try {
        File(path).readText().right()
    } catch (e: IOException) {
        e.left()
    }
}

// BAD: Throws exceptions
fun parseFileBad(path: String): Content {
    return File(path).readText() // May throw IOException
}
```

### 2. Use `fold` for Complete Error Handling

Prefer `fold` over `getOrElse { throw it }`:

```kotlin
// GOOD: Proper error handling with fold
val result = parseUserInput(input)
result.fold(
    ifLeft = { error -> handleError(error) },
    ifRight = { user -> processUser(user) }
)

// BAD: Anti-pattern - defeats purpose of Either
val result = parseUserInput(input).getOrElse { throw it }
```

### 3. Use `flatMap` / `chain` for Composition

Chain operations that return Either:

```kotlin
// GOOD: Chained operations
val user = createUser(name)
    .flatMap { validateEmail(it) }
    .flatMap { saveToDatabase(it) }

// BETTER: Using ArrowUtils.chain
val user = createUser(name)
    .chain { validateEmail(it) }
    .chain { saveToDatabase(it) }
```

### 4. Use `zip` for Combining Eithers

Combine multiple Either results:

```kotlin
// GOOD: Combining two Eithers
val user = createUser(name)
val profile = createProfile(email)
user.zip(profile) { u, p -> UserWithProfile(u, p) }
```

### 5. Use `left()` and `right()` Extensions

Prefer extension functions for creating Either:

```kotlin
// GOOD: Clear intent
ValidationException("error").left()
User(name).right()

// ACCEPTABLE: Constructor usage
Either.Left(ValidationException("error"))
Either.Right(User(name))
```

## Common Patterns

### Pattern 1: Validation with Either

```kotlin
data class ValidationException(val errors: List<String>) : Exception()

fun validateUser(user: User): Either<ValidationException, User> {
    val errors = mutableListOf<String>()
    if (user.name.isBlank()) errors.add("Name required")
    if (user.email.isBlank()) errors.add("Email required")

    return if (errors.isEmpty()) {
        user.right()
    } else {
        ValidationException(errors).left()
    }
}
```

### Pattern 2: Chained Validation

```kotlin
fun createUser(name: String, email: String): Either<ValidationException, User> {
    return Either.catch { User(name, email) }
        .mapLeft { ValidationException(listOf(it.message ?: "Unknown error")) }
        .flatMap { validateUser(it) }
}
```

### Pattern 3: Error Accumulation

For collecting multiple errors, consider using a data class:

```kotlin
data class ValidationResult(
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    fun isValid() = errors.isEmpty()

    fun combine(other: ValidationResult): ValidationResult =
        ValidationResult(errors + other.errors, warnings + other.warnings)
}
```

## Utility Functions

Use `ArrowUtils` for common operations:

```kotlin
import io.github.pintowar.bellum.core.functional.ArrowUtils

// Safe catching
val result = ArrowUtils.catching { potentiallyThrowingFunction() }

// Zip two Eithers
val combined = ArrowUtils.zip(either1, either2)

// Sequence a list of Eithers
val list: List<Either<Error, Value>> = ...
val result: Either<Error, List<Value>> = list.sequence()
```

## Anti-Patterns to Avoid

### 1. `getOrElse { throw it }`

This defeats the purpose of Either:

```kotlin
// BAD
fun example(): Result {
    return parseInput().getOrElse { throw it }
}

// GOOD
fun example(): Either<Error, Result> {
    return parseInput()
}
```

### 2. Ignoring Errors

Don't silently ignore Either results:

```kotlin
// BAD: Ignoring the Either
parseUserInput(userId)

// GOOD: Handle the result
parseUserInput(userId).fold(
    ifLeft = { log.error("Failed to parse: $it") },
    ifRight = { user -> processUser(user) }
)
```

### 3. Using Nullable Instead of Either

When you need to communicate *why* something failed, use Either:

```kotlin
// LESS CLEAR
fun findUser(id: String): User? = users[id]

// MORE CLEAR
fun findUser(id: String): Either<UserNotFound, User> =
    users[id]?.right() ?: UserNotFound(id).left()
```

## Integration with Konform

The project uses Konform for validation. Results are converted to Either:

```kotlin
import io.konform.validation.Validation

private val validator = Validation<User> {
    User::name { minLength(2) }
    User::email { email() }
}

fun validateUser(user: User): Either<ValidationException, User> {
    val result = validator.validate(user)
    return if (result.isValid) {
        user.right()
    } else {
        ValidationException(result.errors).left()
    }
}
```

## Testing with Either

Use Kotest Arrow assertions:

```kotlin
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.arrow.core.shouldBeLeft

// Test success case
val result = createUser("valid")
result.shouldBeRight()

// Test failure case
val result = createUser("")
result.shouldBeLeft()
```

## Summary

1. Use `Either` for all error-handling scenarios
2. Prefer `fold` over `getOrElse { throw it }`
3. Use `flatMap`/`chain` for operation composition
4. Use `zip` for combining multiple Eithers
5. Leverage `ArrowUtils` for common patterns
6. Handle errors explicitly, don't ignore them
