package io.github.pintowar.bellum.core.domain

import io.github.pintowar.bellum.core.util.Helper
import java.util.UUID

@JvmInline
value class UuidId<T>(
    private val value: UUID,
) {
    constructor() : this(Helper.uuidV7())

    operator fun invoke() = value
}

typealias ProjectId = UuidId<Project>
typealias TaskId = UuidId<Task>
typealias EmployeeId = UuidId<Employee>
