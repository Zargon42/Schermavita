package dev.riposte.app

import dev.riposte.life.Observation

/** Replace only fully read, still-enabled metric windows; failures and disabled history survive. */
fun mergeHealthReadings(existing: List<Observation>,incoming: List<Observation>,days: Set<String>,successful: Set<String>,enabled: Set<String>): List<Observation> {
    val replace=successful.intersect(enabled)
    return (existing.filterNot {it.origin.source=="Health Connect" && it.metric in replace && it.stamp.day in days}+
        incoming.filter {it.metric in replace}).associateBy {it.id}.values.toList()
}
