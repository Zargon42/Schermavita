package dev.riposte.life

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ChartPeriodsTest {
    private fun date(value: String)=LocalDate.parse(value)
    private val first=date("2023-01-01")
    private val last=date("2026-11-10")
    private fun day(value: String,v: Double,revision: String="old",basis: String="tracking",series: String="mood")=
        ChartDay(date(value),v,series,basis,setOf(revision),"$value: $v ($revision)")

    @Test fun calendarWindowsIncludeLeapDaysAndMondayWeeks() {
        assertEquals(ChartWindow(date("2024-02-01"),date("2024-02-29")),chartWindow(ChartPeriod.MONTH,date("2024-02-12"),first,last))
        assertEquals(ChartWindow(date("2023-12-25"),date("2023-12-31")),chartWindow(ChartPeriod.WEEK,date("2023-12-31"),first,last))
        assertEquals(ChartWindow(date("2024-01-01"),date("2024-01-07")),chartWindow(ChartPeriod.WEEK,date("2024-01-01"),first,last))
        assertEquals(ChartWindow(date("2024-10-01"),date("2024-12-31")),chartWindow(ChartPeriod.QUARTER,date("2024-11-30"),first,last))
        assertEquals(ChartWindow(date("2024-01-01"),date("2024-12-31")),chartWindow(ChartPeriod.YEAR,date("2024-02-29"),first,last))
        assertEquals(ChartWindow(first,last),chartWindow(ChartPeriod.ALL,last,first,last))
    }
    @Test fun trailingWindowsAreInclusiveAndPagingRespectsHistory() {
        for(period in listOf(ChartPeriod.LAST_7,ChartPeriod.LAST_30,ChartPeriod.LAST_90)) {
            val window=chartWindow(period,last,first,last)
            assertEquals(period.trailingDays,window.end.toEpochDay()-window.start.toEpochDay()+1)
            assertEquals(window.start.minusDays(1),pageChart(period,last,-1))
        }
        assertEquals(date("2024-02-01"),pageChart(ChartPeriod.MONTH,date("2024-01-31"),1))
        assertFalse(canPageChart(ChartPeriod.ALL,last,-1,first,last,last))
        assertFalse(canPageChart(ChartPeriod.MONTH,last,1,first,last,last))
        assertTrue(canPageChart(ChartPeriod.MONTH,last,-1,first,last,last))
        assertFalse(canPageChart(ChartPeriod.WEEK,first,-1,first,last,last))
    }
    @Test fun sparsePartialBucketsWeightDaysAndRetainDefinitions() {
        val source=listOf(day("2024-02-01",2.0),day("2024-02-01",2.0),day("2024-02-03",4.0,"new"),day("2024-02-10",100.0))
        val before=source.toList()
        val bins=chartBins(source,ChartWindow(date("2024-02-01"),date("2024-02-04")),ChartBucket.WEEK)
        val bin=bins.single()
        assertEquals(3.0,bin.value,0.0);assertEquals(2,bin.count)
        assertEquals(date("2024-02-01"),bin.start);assertEquals(date("2024-02-04"),bin.end)
        assertEquals(mapOf("old" to 1,"new" to 1),bin.revisions)
        assertEquals(before,source)
        assertEquals(1,chartBins(source,ChartWindow(date("2024-02-01"),date("2024-02-29")),ChartBucket.MONTH).size)
    }
    @Test fun sourcesAndIncompatibleSeriesNeverShareBuckets() {
        val source=listOf(day("2024-02-01",1.0),day("2024-02-01",5.0,basis="calendar"),day("2024-02-01",100.0,series="different-range"))
        val bins=chartBins(source,ChartWindow(first,last),ChartBucket.MONTH)
        assertEquals(3,bins.size);assertEquals(setOf(1.0,5.0,100.0),bins.map {it.value}.toSet())
        assertTrue(bins.all {it.count==1})
    }
    @Test fun linesBreakAfterTwoMissingDaysOrEmptyBuckets() {
        fun bin(value: String,bucket: ChartBucket)=chartBins(listOf(day(value,2.0)),ChartWindow(first,last),bucket).single()
        assertTrue(connectChartBins(bin("2024-02-01",ChartBucket.DAY),bin("2024-02-03",ChartBucket.DAY),ChartBucket.DAY))
        assertFalse(connectChartBins(bin("2024-02-01",ChartBucket.DAY),bin("2024-02-04",ChartBucket.DAY),ChartBucket.DAY))
        assertTrue(connectChartBins(bin("2024-02-01",ChartBucket.WEEK),bin("2024-02-08",ChartBucket.WEEK),ChartBucket.WEEK))
        assertFalse(connectChartBins(bin("2024-02-01",ChartBucket.WEEK),bin("2024-02-15",ChartBucket.WEEK),ChartBucket.WEEK))
        assertTrue(connectChartBins(bin("2024-02-01",ChartBucket.MONTH),bin("2024-03-31",ChartBucket.MONTH),ChartBucket.MONTH))
        assertFalse(connectChartBins(bin("2024-02-01",ChartBucket.MONTH),bin("2024-04-01",ChartBucket.MONTH),ChartBucket.MONTH))
    }
    @Test fun bucketingFollowsDailyPrecedenceWithoutChangingAnalysisInputs() {
        val old=Metric("m","Mood")
        val metric=old.edited(old.definition().copy(name="Mood renamed"))
        val observations=listOf(
            Observation("instant","m",dev.riposte.core.Stamp(day="2024-02-01"),1.0,scale=old.scale),
            Observation("review","m",dev.riposte.core.Stamp(day="2024-02-01"),5.0,scope="daily",scale=metric.scale),
            Observation("next","m",dev.riposte.core.Stamp(day="2024-02-03"),3.0,scale=old.scale))
        fun values()=compatibleDailyValues(observations,metric,true,setOf(old.scale,metric.scale),setOf("tracking"))
        val before=values()
        val bins=chartBins(before.map {ChartDay(date(it.day),it.value,it.scale,it.basis,setOf(it.revision),it.day)},ChartWindow(first,last),ChartBucket.MONTH)
        assertEquals(2,bins.single().count);assertEquals(4.0,bins.single().value,0.0)
        assertEquals(before,values());assertEquals(3,observations.size)
    }
}
