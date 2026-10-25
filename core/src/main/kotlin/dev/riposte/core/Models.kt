package dev.riposte.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.*
import java.util.UUID

val codec = Json { ignoreUnknownKeys = true; encodeDefaults = true }
fun newId(): String = UUID.randomUUID().toString()

@Serializable data class Origin(
    val source: String = "manual", val externalId: String = "", val batchId: String = "",
    val precision: String = "instant", val dateBasis: String = "tracking", val raw: String = "",
    val uncertain: Boolean = false, val attachmentId: String = "", val inferred: Boolean = false
)
@Serializable data class Stamp(
    val occurredAt: String = Instant.now().toString(), val createdAt: String = Instant.now().toString(),
    val zone: String = ZoneId.systemDefault().id, val day: String = "", val dayOverride: Boolean = false
)
@Serializable data class BoundaryRule(val from: String = "1970-01-01T00:00:00Z", val minutes: Int = 240)
@Serializable data class TrackingDay(
    val id: String, val label: String, val start: String, val end: String, val zone: String,
    val notes: String = "", val willingness: Int? = null
)
@Serializable data class Settings(
    val boundaryRules: List<BoundaryRule> = listOf(BoundaryRule()),
    val reminderEnabled: Boolean = true, val reminderMinutes: Int = 1290,
    val activeSession: String = "", val lastHealthSync: String = "", val healthStatus: String = "Not connected",
    val defaultWeapon: String = "Foil", val lastBackup: String = "",
    val screenTimeEnabled: Boolean = false, val lastScreenTimeSync: String = "", val screenTimeStatus: String = "Not connected",
    val sessionTrackers: List<TrackerRevision> = defaultSessionTrackers(),
    val equipmentCategories: List<EquipmentCategory> = emptyList(),
    val tacticalTags: List<String> = listOf("Distance", "Timing", "Preparation", "Attack", "Parry / riposte", "Counterattack", "Footwork", "Focus")
)
@Serializable data class ImportBatch(val id: String, val name: String, val hash: String,
    val createdAt: String = Instant.now().toString(), val count: Int = 0, val notes: String = "")
@Serializable data class SourceAttachment(val id: String, val name: String, val mime: String, val file: String, val batchId: String)

/** Intervals are real instants. Local cutoffs may produce 23/25-hour days at DST. */
object DayClock {
    fun day(at: Instant, zone: ZoneId, rules: List<BoundaryRule>, existing: List<TrackingDay> = emptyList()): TrackingDay {
        existing.firstOrNull { at >= Instant.parse(it.start) && at < Instant.parse(it.end) }?.let { return it }
        val sorted = rules.sortedBy { Instant.parse(it.from) }
        val rule = sorted.lastOrNull { at >= Instant.parse(it.from) } ?: BoundaryRule()
        val cutoff = LocalTime.of(rule.minutes / 60, rule.minutes % 60)
        val local = at.atZone(zone)
        val effective = Instant.parse(rule.from)
        val transitionDate = effective.atZone(zone).toLocalDate()
        val transitionEnd = transitionDate.plusDays(1).atTime(cutoff).atZone(zone).toInstant()
        if (effective > Instant.EPOCH && at >= effective && at < transitionEnd) {
            return TrackingDay("${zone.id}|$effective",transitionDate.toString(),effective.toString(),transitionEnd.toString(),zone.id)
        }
        val todayBoundary=local.toLocalDate().atTime(cutoff).atZone(zone).toInstant()
        val label = if (at < todayBoundary) local.toLocalDate().minusDays(1) else local.toLocalDate()
        var start = label.atTime(cutoff).atZone(zone).toInstant()
        var end = label.plusDays(1).atTime(cutoff).atZone(zone).toInstant()
        // A changed cutoff starts exactly where the preceding tracking day ended.
        if (effective > start && effective <= at) start = effective
        if (effective > Instant.EPOCH && at >= effective && effective.atZone(zone).toLocalDate() == local.toLocalDate() && local.toLocalTime() < cutoff) {
            start = effective
        }
        sorted.firstOrNull { Instant.parse(it.from) > at && Instant.parse(it.from) < end }?.let { end = Instant.parse(it.from) }
        existing.filter { Instant.parse(it.end) <= at && Instant.parse(it.end) > start }.maxByOrNull { it.end }?.let { start=Instant.parse(it.end) }
        return TrackingDay("${zone.id}|$start", label.toString(), start.toString(), end.toString(), zone.id)
    }
    fun change(rules: List<BoundaryRule>, at: Instant, zone: ZoneId, minutes: Int, existing: List<TrackingDay>): List<BoundaryRule> {
        require(minutes in 0..1439)
        val current = day(at, zone, rules, existing)
        return rules.filter { Instant.parse(it.from) < Instant.parse(current.end) } + BoundaryRule(current.end, minutes)
    }
    fun stamp(at: Instant, settings: Settings, days: List<TrackingDay>, override: String? = null, zone: ZoneId = ZoneId.systemDefault()): Stamp =
        Stamp(at.toString(), Instant.now().toString(), zone.id, override ?: day(at, zone, settings.boundaryRules, days).label, override != null)
}
