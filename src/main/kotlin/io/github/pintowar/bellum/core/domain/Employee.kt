package io.github.pintowar.bellum.core.domain

import io.github.pintowar.bellum.core.util.Helper
import io.konform.validation.Validation
import io.konform.validation.constraints.notBlank
import java.util.UUID

@JvmInline value class EmployeeId(
    private val value: UUID,
) {
    constructor() : this(Helper.uuidV7())

    operator fun invoke() = value
}

class Employee private constructor(
    val id: EmployeeId,
    val name: String,
    val skills: Map<String, SkillPoint>,
) {
    companion object {
        private val validator =
            Validation<Employee> {
                Employee::name {
                    notBlank()
                }
            }

        operator fun invoke(
            id: UUID,
            name: String,
            skills: Map<String, SkillPoint> = emptyMap(),
        ): Result<Employee> =
            runCatching {
                Employee(EmployeeId(id), name, skills).also {
                    val res = validator.validate(it)
                    if (!res.isValid) throw ValidationException(res.errors.toValidationErrorDetails())
                }
            }

        operator fun invoke(
            name: String,
            skills: Map<String, SkillPoint> = emptyMap(),
        ): Result<Employee> = invoke(EmployeeId()(), name, skills)
    }
}
