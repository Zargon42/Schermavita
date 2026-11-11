package dev.riposte.life

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class ChartPeriod(val label: String,val bucket: ChartBucket,val trailingDays: Long=0) {
    WEEK("Week",ChartBucket.DAY), MONTH("Month",ChartBucket.DAY),
    QUARTER("3M",ChartBucket.WEEK), YEAR("Year",ChartBucket.MONTH), ALL("All time",ChartBucket.MONTH),
    LAST_7("Last 7 days",ChartBucket.DAY,7), LAST_30("Last 30 days",ChartBucket.DAY,30), LAST_90("Last 90 days",ChartBucket.WEEK,90)
}
enum class ChartBucket { DAY,WEEK,MONTH }
data class ChartWindow(val start: LocalDate,val end: LocalDate) {
    operator fun contains(day: LocalDate)=day>=start && day<=end
}
fun chartWindow(period: ChartPeriod,anchor: LocalDate,first: LocalDate,last: LocalDate): ChartWindow {
    if(period.trailingDays>0) return ChartWindow(anchor.minusDays(period.trailingDays-1),anchor)
    val start=when(period) {
        ChartPeriod.WEEK->anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        ChartPeriod.MONTH->anchor.withDayOfMonth(1)
        ChartPeriod.QUARTER->anchor.withMonth((anchor.monthValue-1)/3*3+1).withDayOfMonth(1)
        ChartPeriod.YEAR->anchor.withDayOfYear(1)
        else->first
    }
    val end=when(period) {
        ChartPeriod.WEEK->start.plusDays(6)
        ChartPeriod.MONTH->start.plusMonths(1).minusDays(1)
        ChartPeriod.QUARTER->start.plusMonths(3).minusDays(1)
        ChartPeriod.YEAR->start.plusYears(1).minusDays(1)
        else->last
    }
    return ChartWindow(start,end)
}
fun pageChart(period: ChartPeriod,anchor: LocalDate,direction: Int): LocalDate = when {
    period.trailingDays>0->anchor.plusDays(direction*period.trailingDays)
    period==ChartPeriod.WEEK->anchor.plusWeeks(direction.toLong())
    period==ChartPeriod.MONTH->anchor.withDayOfMonth(1).plusMonths(direction.toLong())
    period==ChartPeriod.QUARTER->anchor.withDayOfMonth(1).plusMonths(direction*3L)
    period==ChartPeriod.YEAR->anchor.withDayOfYear(1).plusYears(direction.toLong())
    else->anchor
}
fun canPageChart(period: ChartPeriod,anchor: LocalDate,direction: Int,first: LocalDate,last: LocalDate,today: LocalDate): Boolean {
    if(period==ChartPeriod.ALL) return false
    val next=chartWindow(period,pageChart(period,anchor,direction),first,last)
    return next.end>=first && next.start<=minOf(last,today)
}

/** Already-resolved daily measurements; series separates incompatible scales/measures. */
data class ChartDay(val day: LocalDate,val value: Double,val series: String,val basis: String,
    val revisions: Set<String>,val detail: String)
data class ChartBin(val start: LocalDate,val end: LocalDate,val value: Double,val series: String,val basis: String,
    val count: Int,val revisions: Map<String,Int>,val details: List<String>)
fun bucketStart(day: LocalDate,bucket: ChartBucket): LocalDate=when(bucket) {
    ChartBucket.DAY->day
    ChartBucket.WEEK->day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    ChartBucket.MONTH->day.withDayOfMonth(1)
}
fun bucketEnd(start: LocalDate,bucket: ChartBucket): LocalDate=when(bucket) {
    ChartBucket.DAY->start
    ChartBucket.WEEK->start.plusDays(6)
    ChartBucket.MONTH->start.plusMonths(1).minusDays(1)
}
fun chartBins(days: List<ChartDay>,window: ChartWindow,bucket: ChartBucket): List<ChartBin> =
    days.filter {it.day in window}.groupBy {Triple(it.day,it.series,it.basis)}.values.map {sameDay->
        // Defensive deduplication: each recorded day has equal weight, even for repeated input rows.
        sameDay.first().copy(value=sameDay.map {it.value}.average(),revisions=sameDay.flatMap {it.revisions}.toSet(),
            detail=sameDay.map {it.detail}.distinct().joinToString("\n"))
    }.groupBy {Triple(bucketStart(it.day,bucket),it.series,it.basis)}.map {(key,rows)->
        ChartBin(maxOf(key.first,window.start),minOf(bucketEnd(key.first,bucket),window.end),
            rows.map {it.value}.average(),key.second,key.third,rows.size,
            rows.flatMap {it.revisions}.groupingBy {it}.eachCount(),rows.map {it.detail})
    }.sortedWith(compareBy<ChartBin> {it.start}.thenBy {it.series}.thenBy {it.basis})

fun connectChartBins(a: ChartBin,b: ChartBin,bucket: ChartBucket): Boolean {
    if(a.series!=b.series || a.basis!=b.basis) return false
    return when(bucket) {
        ChartBucket.DAY->b.start.toEpochDay()-a.start.toEpochDay() in 1L..2L
        ChartBucket.WEEK->bucketStart(a.start,bucket).plusWeeks(1)==bucketStart(b.start,bucket)
        ChartBucket.MONTH->bucketStart(a.start,bucket).plusMonths(1)==bucketStart(b.start,bucket)
    }
}
