package io.github.pintowar.bellum.core.domain

import arrow.core.Either
import io.konform.validation.Validation
import io.konform.validation.constraints.notBlank
import java.util.UUID

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
        ): Either<ValidationException, Employee> = Employee(EmployeeId(id), name, skills).validateAndWrap(validator)

        operator fun invoke(
            name: String,
            skills: Map<String, SkillPoint> = emptyMap(),
        ): Either<ValidationException, Employee> = invoke(EmployeeId()(), name, skills)
    }
}
