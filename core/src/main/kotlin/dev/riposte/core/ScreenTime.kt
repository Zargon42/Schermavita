package dev.riposte.core

/** Android-independent reconstruction of confirmed interactive, unlocked time. */
object ScreenTime {
    enum class Kind { ON, OFF, UNLOCK, LOCK, RESET }
    data class Event(val at: Long, val kind: Kind)
    data class Reading(val milliseconds: Long, val unknownMilliseconds: Long)
    fun measure(events: List<Event>, start: Long, end: Long): Reading {
        require(end >= start)
        var interactive: Boolean? = null
        var locked: Boolean? = null
        var cursor = start
        var used = 0L
        var unknown = 0L
        fun account(until: Long) {
            val stop = until.coerceIn(start, end)
            val duration = (stop - cursor).coerceAtLeast(0)
            when {
                interactive == false || locked == true -> Unit
                interactive == true && locked == false -> used += duration
                else -> unknown += duration
            }
            cursor = maxOf(cursor, stop)
        }
        for (event in events.sortedBy { it.at }) {
            if (event.at >= end) break
            account(event.at)
            when (event.kind) {
                Kind.ON -> interactive = true
                Kind.OFF -> interactive = false
                Kind.UNLOCK -> { locked = false; interactive = true }
                Kind.LOCK -> locked = true
                Kind.RESET -> { interactive = null; locked = null }
            }
        }
        account(end)
        return Reading(used, unknown)
    }
}
