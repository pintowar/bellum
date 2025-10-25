package io.github.pintowar.rts.core.domain

import io.konform.validation.ValidationError

class ValidationException(val errors: List<ValidationError>) : Throwable()

fun List<AssignedTask>.hasOverlappingIntervals(): Boolean {
    // An empty list or a list with a single interval cannot have overlaps.
    if (this.size <= 1) {
        return false
    }

    // Sort the intervals based on their start time.
    val sortedIntervals = this.sortedBy { it.startAt }

    // Iterate through the sorted intervals, starting from the second one.
    for (i in 1 until sortedIntervals.size) {
        val currentInterval = sortedIntervals[i]
        val previousInterval = sortedIntervals[i - 1]

        // If the start of the current interval is before the end of the previous one,
        // there is an overlap.
        if (currentInterval.overlaps(previousInterval)) {
            return true
        }
    }

    // If the loop completes without finding any overlaps, return false.
    return false
}