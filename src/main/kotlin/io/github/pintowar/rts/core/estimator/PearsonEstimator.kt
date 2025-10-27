package io.github.pintowar.rts.core.estimator

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation
import java.time.Duration
import kotlin.math.roundToLong

class PearsonEstimator : TimeEstimator {
    override fun estimate(
        employeeSkills: Array<Int>,
        taskSkills: Array<Int>,
    ): Result<Duration> =
        runCatching {
            check(employeeSkills, taskSkills)

            val pearson = PearsonsCorrelation()
            val correlation =
                pearson.correlation(
                    DoubleArray(employeeSkills.size) { employeeSkills[it].toDouble() },
                    DoubleArray(taskSkills.size) { taskSkills[it].toDouble() },
                )
            val corr = if (correlation.isNaN()) 0.0 else correlation
            val time = 5 + (40 * (1 - corr)).roundToLong()
            Duration.ofMinutes(time)
        }
}
