package io.github.pintowar.bellum.estimator

import io.github.pintowar.bellum.core.estimator.TimeEstimator
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class PearsonEstimator : TimeEstimator() {
    override fun skillsEstimation(
        employeeSkills: Array<Int>,
        taskSkills: Array<Int>,
    ): Result<Duration> =
        validateSkillSets(employeeSkills, taskSkills).map {
            val pearson = PearsonsCorrelation()
            val correlation =
                pearson.correlation(
                    DoubleArray(employeeSkills.size) { employeeSkills[it].toDouble() },
                    DoubleArray(taskSkills.size) { taskSkills[it].toDouble() },
                )
            val corr = if (correlation.isNaN()) 0.0 else correlation
            val time = 5 + (40 * (1 - corr)).roundToLong()
            time.minutes
        }
}
