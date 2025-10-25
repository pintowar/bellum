package io.github.pintowar.rts.core.domain

import io.github.pintowar.rts.core.util.Helper
import io.konform.validation.Validation
import io.konform.validation.constraints.notBlank
import java.util.UUID

@JvmInline value class EmployeeId(
    private val id: UUID,
) {
    constructor() : this(Helper.uuidV7())

    operator fun invoke() = id
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

        fun valueOf(
            id: UUID,
            name: String,
            skills: Map<String, SkillPoint> = emptyMap(),
        ): Result<Employee> =
            runCatching {
                Employee(EmployeeId(id), name, skills).also {
                    val res = validator.validate(it)
                    if (!res.isValid) throw ValidationException(res.errors)
                }
            }

        fun valueOf(
            name: String,
            skills: Map<String, SkillPoint> = emptyMap(),
        ): Result<Employee> = valueOf(EmployeeId()(), name, skills)
    }
}
