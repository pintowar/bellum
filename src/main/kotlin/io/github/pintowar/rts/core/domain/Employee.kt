package io.github.pintowar.rts.core.domain

import arrow.core.Either
import arrow.core.raise.either
import io.github.pintowar.rts.core.util.Helper
import java.util.UUID

interface InvalidEmployee
object InvalidEmployeeName: InvalidEmployee

@JvmInline value class EmployeeId(private val id: UUID) {
    constructor(): this(Helper.uuidV7())

    operator fun invoke() = id
}

@JvmInline value class EmployeeName private constructor(private val name: String) {
    companion object {
        fun valueOf(name: String): Either<InvalidEmployeeName, EmployeeName> = either {
            if (name.isBlank()) raise(InvalidEmployeeName)
            EmployeeName(name)
        }
    }

    operator fun invoke() = name
}

data class Employee(val id: EmployeeId, val name: EmployeeName, val skills: Map<String, SkillPoint>) {
    companion object {
        fun valueOf(id: UUID, name: String, skills: Map<String, SkillPoint> = emptyMap()): Either<InvalidEmployee, Employee> = either {
            valueOf(name, skills).bind().copy(id = EmployeeId(id))
        }

        fun valueOf(name: String, skills: Map<String, SkillPoint> = emptyMap()): Either<InvalidEmployee, Employee> = either {
            val employeeName = EmployeeName.valueOf(name).bind()
            Employee(EmployeeId(), employeeName, skills)
        }
    }
}