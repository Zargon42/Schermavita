package dev.riposte.app

import dev.riposte.core.*
import dev.riposte.fencing.*
import dev.riposte.insights.*
import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class UnifiedFeaturesTest {
    @Test fun healthSyncRetainsFailedDisabledAndManualReadingsWithoutDuplicates() {
        fun reading(id: String,metric: String,source: String="Health Connect")=dev.riposte.life.Observation(id,metric,Stamp(day="2026-10-01"),10.0,origin=Origin(source))
        val existing=listOf(reading("step","steps"),reading("sleep","sleep"),reading("manual","steps","manual"))
        val incoming=listOf(reading("step","steps").copy(value=20.0))
        val result=mergeHealthReadings(existing,incoming,setOf("2026-10-01"),setOf("steps"),setOf("steps","sleep"))
        assertEquals(3,result.size);assertEquals(20.0,result.single {it.id=="step"}.value!!,0.0)
        assertEquals(existing.filter {it.id!="step"},result.filter {it.id!="step"})
        assertEquals(result,mergeHealthReadings(result,incoming,setOf("2026-10-01"),setOf("steps"),setOf("steps")))
        assertEquals(existing,mergeHealthReadings(existing,incoming,setOf("2026-10-01"),setOf("steps"),emptySet()))
    }
    private fun fixture(): Snapshot {
        val session=Session("s","Practice",Stamp(day="2026-10-01"),sword="Foil A",focus="Keep distance")
        return Snapshot(fencers=listOf(Fencer("me","Me",true),Fencer("other","Other")),sessions=listOf(session),
            bouts=listOf(Bout("b","s","me","other",5,3,stamp=session.stamp,detailsA=ParticipantDetails(sword=" foil  a ",glove="G",lenses=true,process=4.5),detailsB=ParticipantDetails(sword="Foil A"))))
    }
    @Test fun equipmentMigrationIsIdempotentAndRespectsOwnership() {
        val s=fixture().migrateEquipment();assertEquals(s,s.migrateEquipment())
        assertEquals(3,s.equipmentProfiles.size)
        assertEquals(s.sessions.single().swordId,s.bouts.single().detailsA.swordId)
        assertNotEquals(s.bouts.single().detailsA.swordId,s.bouts.single().detailsB.swordId)
        assertEquals(" foil  a ",s.bouts.single().detailsA.sword)
    }
    @Test fun duplicateMergePreservesNamesAndComponentStatistics() {
        val s=fixture().migrateEquipment();val source=s.equipmentProfiles.first {it.owner=="me" && it.kind=="Foil"}
        val target=source.copy(id="replacement",name="Competition foil",blade="BF",retired=true)
        val merged=s.copy(equipmentProfiles=s.equipmentProfiles+target).mergeEquipment(source.id,target.id)
        assertEquals(target.id,merged.bouts.single().detailsA.swordId)
        assertEquals("BF",equipmentValue(merged,merged.bouts.single().detailsA,"Blade type"))
        assertTrue(merged.equipmentProfiles.single {it.id==target.id}.aliases.contains(source.name))
        assertEquals(s.bouts.single().detailsA.sword,merged.bouts.single().detailsA.sword)
    }
    @Test fun filtersPreserveUnknownAndProcessRatingIsIndependent() {
        val s=fixture().migrateEquipment();val bout=s.bouts.single()
        assertTrue(matchesSessionFilters(s,bout,mapOf("Contact lenses" to "Yes","warmup" to "Unknown")))
        assertFalse(matchesSessionFilters(s,bout,mapOf("Contact lenses" to "No")))
        assertNull(statistics(s.bouts,s.me).execution)
        assertEquals(4.5,performancePoints(s.bouts,s.me,PerformanceMeasure.PROCESS).single().value,0.0)
        assertEquals("Keep distance",s.sessions.single().sessionGoals().single().text)
        assertTrue(s.sessions.single().copy(goalsConfigured=true,goals=emptyList()).sessionGoals().isEmpty())
        val unanswered=ParticipantDetails().withGoalSnapshots(listOf(SessionGoal("g","Original")))
        assertNull(unanswered.goalAnswers.single().applied)
        assertEquals("Original",unanswered.withGoalSnapshots(listOf(SessionGoal("g","Changed"))).goalAnswers.single().text)
    }
    @Test fun reflectionAndGoalSnapshotsSurviveSerializationAndTemplateEdits() {
        val question=ReflectionQuestion(title="What worked?")
        val reflection=SessionReflection("Notes",listOf(ReflectionAnswer(question,"Distance")),"Complete")
        val base=fixture().migrateEquipment()
        val snapshot=base.copy(reflectionQuestions=listOf(question.copy(title="New title",archived=true)),sessions=base.sessions.map {it.copy(reflection=reflection,goals=listOf(SessionGoal("g","New goal")))},
            bouts=base.bouts.map {it.copy(detailsA=it.detailsA.copy(goalAnswers=listOf(GoalAnswer("g","Original goal",false))))})
        val restored=codec.decodeFromString<Snapshot>(codec.encodeToString(snapshot));restored.validate()
        assertEquals(snapshot,restored);assertEquals("What worked?",restored.sessions.single().reflection!!.answers.single().question.title)
        assertEquals("Original goal",restored.bouts.single().detailsA.goalAnswers.single().text)
    }
}
