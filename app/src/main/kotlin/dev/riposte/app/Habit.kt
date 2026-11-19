package dev.riposte.app

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

@Serializable enum class HabitType { CheckIns, Quantity, Sessions }
@Serializable enum class HabitPeriod { Days, Weeks, Months }
@Serializable data class HabitGoal(
    val type: HabitType = HabitType.CheckIns, val target: Double = 1.0,
    val every: Int = 1, val period: HabitPeriod = HabitPeriod.Days,
    val countdown: Boolean = false, val unit: String = "", val threshold: Double = 1.0
) {
    val binary get() = type == HabitType.CheckIns && target == 1.0
    fun validate() {
        require(target.isFinite() && target > 0 && every in 1..999)
        require(type == HabitType.Quantity || target % 1.0 == 0.0)
        require(type == HabitType.CheckIns || unit.isNotBlank())
        require(threshold.isFinite() && threshold > 0)
        require(!countdown || (period == HabitPeriod.Days && type != HabitType.Quantity && target == 1.0))
    }
    fun label(): String {
        val amount = when (type) {
            HabitType.Quantity -> "${habitNumber(target)} $unit"
            HabitType.Sessions -> "${habitNumber(target)} × ${habitNumber(threshold)} $unit"
            HabitType.CheckIns -> if (binary) "Once" else "${habitNumber(target)} times"
        }
        val span = when (period) {
            HabitPeriod.Days -> if (every == 1) "day" else "$every days"
            HabitPeriod.Weeks -> if (every == 1) "calendar week" else "$every calendar weeks"
            HabitPeriod.Months -> if (every == 1) "calendar month" else "$every calendar months"
        }
        return if (countdown) "$amount every $span · countdown" else "$amount per $span"
    }
    fun nextBoundary(start: LocalDate): LocalDate = when (period) {
        HabitPeriod.Days -> start.plusDays(every.toLong())
        HabitPeriod.Weeks -> start.minusDays(start.dayOfWeek.value - 1L).plusWeeks(every.toLong())
        HabitPeriod.Months -> start.withDayOfMonth(1).plusMonths(every.toLong())
    }
}

@Serializable data class HabitRevision(val from: String, val goal: HabitGoal)
@Serializable data class Habit(val name: String, val revisions: List<HabitRevision>,
    val id: String = UUID.randomUUID().toString(), val archived: Boolean = false) {
    val start get() = LocalDate.parse(revisions.first().from)
    fun goalOn(day: LocalDate) = revisions.last { LocalDate.parse(it.from) <= day }.goal
    fun validate() {
        require(id.isNotBlank() && name.isNotBlank() && revisions.isNotEmpty())
        val dates = revisions.map { LocalDate.parse(it.from) }
        require(dates.first().year >= 1900 && dates.zipWithNext().all { (a,b) -> a < b })
        revisions.forEach { it.goal.validate() }
    }
}

@Serializable data class HabitEntry(val habitId: String, val day: String, val amount: Double = 1.0,
    val id: String = UUID.randomUUID().toString())

fun habitNumber(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

data class HabitStats(val progress: BigDecimal, val from: LocalDate, val through: LocalDate,
    val current: Int, val best: Int, val consistency: Int?, val completedToday: Boolean)

fun HabitStats.nextPeriodStart(today: LocalDate): LocalDate =
    if (completedToday) today.plusDays(1) else through.plusDays(1)

private class HabitScore {
    var current = 0
    var best = 0
    private var credit = 0.0
    private var weight = 0.0
    private val decay = 0.5.pow(0.1)
    val percent get() = if (weight == 0.0) null else (100 * credit / weight).roundToInt()
    fun add(fraction: Double) {
        current = if (fraction >= 1.0) current + 1 else 0
        best = maxOf(best, current)
        credit = decay * credit + fraction.coerceAtMost(1.0)
        weight = decay * weight + 1
    }
}

fun Habit.stats(entries: List<HabitEntry>, today: LocalDate): HabitStats {
    require(today >= start)
    val days = entries.filter { it.habitId == id }.groupBy { LocalDate.parse(it.day) }
    val score = HabitScore()
    var progress = BigDecimal.ZERO
    var from = start
    var through = start
    var completedToday = false
    for ((index, revision) in revisions.withIndex()) {
        val goal = revision.goal
        val begin = LocalDate.parse(revision.from)
        if (begin > today) break
        val nextRevision = revisions.getOrNull(index + 1)?.let { LocalDate.parse(it.from) }
        val end = minOf(today, nextRevision?.minusDays(1) ?: today)
        var cursor = begin
        while (cursor <= end) {
            from = cursor
            through = minOf(goal.nextBoundary(cursor).minusDays(1), nextRevision?.minusDays(1) ?: LocalDate.MAX)
            val last = minOf(end, through)
            var day = from
            progress = BigDecimal.ZERO
            var countdownHit: LocalDate? = null
            while (day <= last) {
                val logs = days[day].orEmpty()
                val amount = when (goal.type) {
                    HabitType.CheckIns -> if (goal.binary) minOf(1, logs.size).toBigDecimal() else logs.size.toBigDecimal()
                    HabitType.Quantity -> logs.sumOf { BigDecimal.valueOf(it.amount) }
                    HabitType.Sessions -> logs.count { it.amount >= goal.threshold }.toBigDecimal()
                }
                progress += amount
                if (goal.countdown && amount.signum() > 0) {
                    countdownHit = day
                    break
                }
                day = day.plusDays(1)
            }
            val met = progress >= BigDecimal.valueOf(goal.target)
            val shortOpening = cursor == begin && when (goal.period) {
                HabitPeriod.Weeks -> begin.dayOfWeek.value != 1
                HabitPeriod.Months -> begin.dayOfMonth != 1
                HabitPeriod.Days -> false
            }
            if (met || (through < today && !shortOpening)) score.add(if (met) 1.0 else progress.toDouble() / goal.target)
            if (countdownHit != null) {
                completedToday = countdownHit == today
                cursor = countdownHit.plusDays(1)
                if (completedToday) through = minOf(countdownHit.plusDays(goal.every.toLong()), nextRevision?.minusDays(1) ?: LocalDate.MAX)
            } else cursor = through.plusDays(1)
        }
    }
    return HabitStats(progress, from, through, score.current, score.best, score.percent, completedToday)
}

