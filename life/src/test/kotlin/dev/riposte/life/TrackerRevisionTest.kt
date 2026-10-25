package dev.riposte.life
import dev.riposte.core.*
import org.junit.Assert.*
import org.junit.Test
class TrackerRevisionTest {
    @Test fun reducingAndExpandingScalesPreservesHistory() {
        val original=Metric("mood","Mood")
        val reduced=original.edited(original.definition().copy(max=3.0,levels=listOf(RatingLevel(1.0,"Low"),RatingLevel(2.0,"Okay"),RatingLevel(3.0,"High"))))
        assertEquals(original.id,reduced.id);assertNotEquals(original.scale,reduced.scale)
        assertEquals(original.definition(),reduced.atScale(original.scale).definition())
        val expanded=reduced.edited(reduced.definition().copy(max=12.0,levels=(1..12).map {RatingLevel(it.toDouble(),"Level $it")}))
        assertEquals(12,expanded.definition().levels.size)
        assertEquals(reduced.definition(),expanded.atScale(reduced.scale).definition())
        val events=listOf(Observation(metric=original.id,stamp=Stamp(day="2026-10-07"),value=5.0,scale=original.scale),Observation(metric=original.id,stamp=Stamp(day="2026-10-07"),value=3.0,scale=reduced.scale))
        assertEquals(2,dailyValues(events,expanded).size)
    }
    @Test fun changedLabelsAndUnitsCreateRevisionsButNoOpDoesNot() {
        val m=Metric("duration","Duration",kind="Duration",unit="minutes")
        assertEquals(m.scale,m.edited(m.definition()).scale)
        val changed=m.edited(m.definition().copy(unit="hours"))
        assertEquals("minutes",changed.atScale(m.scale).unit)
        val rating=Metric("mood","Mood")
        assertNotEquals(rating.scale,rating.edited(rating.definition().copy(levels=rating.definition().levels.map {it.copy(label="Label "+it.value)})).scale)
    }
    @Test fun rejectsInvalidLevelsAndChangingType() {
        val m=Metric("mood","Mood")
        assertThrows(IllegalArgumentException::class.java) {m.edited(m.definition().copy(levels=listOf(RatingLevel(1.0,"A"),RatingLevel(1.0,"B"))))}
        assertThrows(IllegalArgumentException::class.java) {m.edited(m.definition().copy(kind="Text"))}
    }
}
