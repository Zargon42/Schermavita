package dev.riposte.app

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class HabitTest {
    private fun habit(goal: HabitGoal, start: String="2026-06-01") = Habit("Practice",listOf(HabitRevision(start,goal)))
    private fun Habit.log(day: String,value: Double=1.0) = HabitEntry(id,day,value)
    private fun Habit.at(day: String,vararg entries: HabitEntry) = stats(entries.toList(),LocalDate.parse(day))

    @Test fun dailyCountsAndCoverageRecoverAfterCorrection() {
        val h=habit(HabitGoal(target=3.0))
        val first=List(3) {h.log("2026-06-01")}
        val second=List(2) {h.log("2026-06-02")}
        assertEquals(1,h.stats(first+second,LocalDate.parse("2026-06-02")).current)
        val missed=h.stats(first+second,LocalDate.parse("2026-06-03"))
        assertEquals(0,missed.current)
        assertEquals(1,missed.best)
        assertEquals(83,missed.consistency)
        assertEquals(2,h.stats(first+second+h.log("2026-06-02"),LocalDate.parse("2026-06-03")).current)
        assertTrue(h.stats(first,LocalDate.parse("2026-06-15")).consistency!! < 10)
    }

    @Test fun countdownAndFixedBlocksDivergeOnEarlyAndLateLogs() {
        val goal=HabitGoal(every=3,countdown=true)
        val h=habit(goal)
        val tuesday=h.log("2026-06-02")
        assertEquals("2026-06-05",h.at("2026-06-02",tuesday).through.toString())
        assertEquals(1,h.at("2026-06-05",tuesday).current)
        assertEquals(0,h.at("2026-06-06",tuesday).current)
        val late=h.at("2026-06-06",tuesday,h.log("2026-06-06"))
        assertEquals(1,late.current)
        assertTrue(late.consistency!! in 60..70)
        val fixed=habit(goal.copy(countdown=false))
        assertEquals("2026-06-03",fixed.at("2026-06-02",fixed.log("2026-06-02")).through.toString())
    }

    @Test fun calendarBoundariesAndShortOpeningPeriods() {
        val h=habit(HabitGoal(period=HabitPeriod.Weeks,target=2.0),"2026-06-03")
        assertNull(h.at("2026-06-08",h.log("2026-06-04")).consistency)
        assertEquals(1,h.at("2026-06-08",h.log("2026-06-04"),h.log("2026-06-05")).current)
        val months=habit(HabitGoal(period=HabitPeriod.Months,every=2),"2024-01-31")
        assertEquals("2024-02-29",months.at("2024-02-01").through.toString())
        assertEquals("2024-03-01",months.at("2024-03-01").from.toString())
    }

    @Test fun quantitiesSessionsAndRevisionsKeepHistoricalMeaning() {
        val goal=HabitGoal(type=HabitType.Quantity,target=0.3,unit="km")
        val h=habit(goal).copy(revisions=listOf(HabitRevision("2026-06-01",goal),
            HabitRevision("2026-06-02",HabitGoal(type=HabitType.Sessions,unit="min",threshold=20.0,target=2.0))))
        val entries=listOf(h.log("2026-06-01",0.1),h.log("2026-06-01",0.2),
            h.log("2026-06-02",10.0),h.log("2026-06-02",10.0),h.log("2026-06-02",25.0))
        val stats=h.stats(entries,LocalDate.parse("2026-06-02"))
        assertEquals(1,stats.current)
        assertEquals(1.0,stats.progress.toDouble(),0.0)
        assertEquals(74,h.stats(entries,LocalDate.parse("2026-06-03")).consistency)
    }

    @Test fun archiveRoundTripAndOldSnapshots() {
        val h=habit(HabitGoal())
        val saved=Snapshot(habits=listOf(h),habitEntries=listOf(h.log("2026-06-01")))
        val packed=BackupArchive.pack(saved) {error("No attachments")}
        val restored=BackupArchive.unpack(packed).snapshot
        assertEquals(saved,restored)
        val legacy=dev.riposte.core.codec.decodeFromString<Snapshot>("{}")
        assertTrue(legacy.habits.isEmpty() && legacy.habitEntries.isEmpty())
        assertThrows(IllegalArgumentException::class.java) {saved.copy(habitEntries=saved.habitEntries+h.log("2026-06-01")).validate()}
    }
}

