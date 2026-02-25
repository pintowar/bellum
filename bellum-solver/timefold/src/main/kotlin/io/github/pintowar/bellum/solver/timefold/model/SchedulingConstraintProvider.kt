package io.github.pintowar.bellum.solver.timefold.model

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore
import ai.timefold.solver.core.api.score.stream.Constraint
import ai.timefold.solver.core.api.score.stream.ConstraintFactory
import ai.timefold.solver.core.api.score.stream.ConstraintProvider
import ai.timefold.solver.core.api.score.stream.Joiners

class SchedulingConstraintProvider : ConstraintProvider {
    override fun defineConstraints(constraintFactory: ConstraintFactory): Array<Constraint> =
        arrayOf(
            pinnedTasksStayFixed(constraintFactory),
            precedenceConstraints(constraintFactory),
            noEmployeeOverlap(constraintFactory),
            minimizeMakespan(constraintFactory),
            minimizePriorityCost(constraintFactory),
        )

    private fun pinnedTasksStayFixed(constraintFactory: ConstraintFactory): Constraint =
        constraintFactory
            .forEach(TaskAssignment::class.java)
            .filter { it.pinned }
            .penalize(
                HardSoftScore.ONE_HARD,
                { taskAssignment ->
                    val empId =
                        taskAssignment.employee
                            ?.id()
                            ?.toString()
                    val startTime = taskAssignment.startTimeMinute ?: 0
                    val expectedEmpId = taskAssignment.pinnedEmployeeId
                    val expectedStartTime = taskAssignment.pinnedStartTimeMinute ?: 0

                    var penalty = 0
                    if (empId != expectedEmpId) penalty += 1000
                    if (startTime != expectedStartTime) penalty += 1000
                    penalty
                },
            ).asConstraint("Pinned tasks must stay fixed")

    private fun precedenceConstraints(constraintFactory: ConstraintFactory): Constraint =
        constraintFactory
            .forEach(TaskAssignment::class.java)
            .join(
                TaskAssignment::class.java,
                Joiners.equal({ it.dependsOnTaskId }, { it.taskId }),
            ).filter { task, predecessor ->
                val taskStart = task.startTimeMinute ?: 0
                val predecessorEnd = (predecessor.startTimeMinute ?: 0) + predecessor.getDuration()
                taskStart < predecessorEnd
            }.penalize(HardSoftScore.ONE_HARD)
            .asConstraint("Precedence constraints")

    private fun noEmployeeOverlap(constraintFactory: ConstraintFactory): Constraint =
        constraintFactory
            .forEachUniquePair(
                TaskAssignment::class.java,
                Joiners.equal { it.employee },
                Joiners.overlapping(
                    { it.startTimeMinute ?: 0 },
                    { (it.startTimeMinute ?: 0) + it.getDuration() },
                ),
            ).penalize(HardSoftScore.ONE_HARD)
            .asConstraint("No employee overlap")

    private fun minimizeMakespan(constraintFactory: ConstraintFactory): Constraint =
        constraintFactory
            .forEach(TaskAssignment::class.java)
            .filter { it.startTimeMinute != null && it.employee != null }
            .penalize(
                HardSoftScore.ONE_SOFT,
                { taskAssignment ->
                    (taskAssignment.startTimeMinute ?: 0) + taskAssignment.getDuration()
                },
            ).asConstraint("Minimize makespan")

    private fun minimizePriorityCost(constraintFactory: ConstraintFactory): Constraint =
        constraintFactory
            .forEachUniquePair(TaskAssignment::class.java)
            .filter { t1, t2 ->
                t1.startTimeMinute != null && t2.startTimeMinute != null
            }.filter { t1, t2 ->
                val p1 = t1.taskPriority.value
                val p2 = t2.taskPriority.value
                if (p1 > p2 && t1.startTimeMinute!! < t2.startTimeMinute!!) {
                    true
                } else {
                    p2 > p1 && t2.startTimeMinute!! < t1.startTimeMinute!!
                }
            }.penalize(HardSoftScore.ONE_SOFT)
            .asConstraint("Minimize priority cost")
}
