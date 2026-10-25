package dev.riposte.insights

import dev.riposte.core.*
import dev.riposte.fencing.*
import dev.riposte.life.*
import org.junit.Assert.*
import org.junit.Test

class FencingAnalysisTest {
    private val people=listOf(Fencer("me","Me",true),Fencer("a","A"),Fencer("b","B"),Fencer("unknown","Unknown",identityKnown=false))
    private val sessions=listOf(Session("s","Training",Stamp(day="2026-10-01")),Session("c","Competition",Stamp(day="2026-10-01"),context="Competition"),Session("e","Épée",Stamp(day="2026-10-01"),weapon="Épée"))
    private fun bout(id: Int,day: Int=id,a: String="me",b: String="a",forScore: Int?=5,against: Int?=3,session: String="s") =
        Bout(id="b$id",sessionId=session,a=a,b=b,scoreA=forScore,scoreB=against,target=5,stamp=Stamp(day="2026-10-%02d".format(day)))
    private fun model(rows: List<Bout>,filter: AnalysisFilter=AnalysisFilter())=opponentStrength(rows,sessions,people,filter)

    @Test fun scoreMarginsAndOrientationDriveSymmetricRatings() {
        val close=model(listOf(bout(1,against=4)))
        val decisive=model(listOf(bout(1,against=0)))
        assertTrue(decisive.ratings.getValue("Foil" to "me").rating>close.ratings.getValue("Foil" to "me").rating)
        assertEquals(3000.0,decisive.ratings.getValue("Foil" to "me").rating+decisive.ratings.getValue("Foil" to "a").rating,1e-10)
        val reversed=model(listOf(bout(1,a="a",b="me",forScore=0,against=5)))
        assertEquals(decisive.ratings,reversed.ratings)
        assertEquals(.5,decisive.comparisons.getValue("b1").expectedA,0.0)
    }
    @Test fun priorDaysOnlyAndSameDayOrderIndependent() {
        val first=bout(1);val second=bout(2,day=1,a="a",b="b",against=0);val third=bout(3,day=2)
        assertEquals(model(listOf(first,second,third)),model(listOf(third,second,first)))
        val before=model(listOf(first,second))
        assertEquals(before.comparisons.getValue("b1"),model(listOf(first,second,third)).comparisons.getValue("b1"))
        assertEquals(.5,before.comparisons.getValue("b2").expectedA,0.0)
        assertEquals(1,model(listOf(first,second,third)).comparisons.getValue("b3").priorA.bouts)
    }
    @Test fun provisionalThresholdAndWeightedAdjustedPerformance() {
        val rows=(1..7).map {bout(it,day=if(it==7) 6 else it,against=if(it==7) 0 else 3)}
        val result=model(rows)
        assertTrue(result.comparisons.getValue("b5").provisional)
        assertFalse(result.comparisons.getValue("b6").provisional)
        assertEquals(5,result.comparisons.getValue("b7").priorB.bouts)
        val points=performancePoints(rows,"me",PerformanceMeasure.ADJUSTED,result)
        assertEquals(1,points.size);assertEquals(2,points.single().count)
        val compared=listOf(result.comparisons.getValue("b6"),result.comparisons.getValue("b7"))
        assertEquals(compared.sumOf {(it.actualA-it.expectedA)*it.touches}*100/compared.sumOf {it.touches},points.single().value,1e-10)
    }
    @Test fun weaponAndOptionalFiltersRetainThirdPartyEvidence() {
        val history=listOf(bout(1,a="a",b="b",against=0),bout(2),bout(3,session="e"),bout(4,session="c"))
        val all=model(history)
        assertTrue(all.comparisons.getValue("b2").expectedA<.5)
        assertEquals(.5,all.comparisons.getValue("b3").expectedA,0.0)
        assertEquals(setOf("b3"),model(history,AnalysisFilter(weapon="Épée")).comparisons.keys)
        assertEquals(setOf("b4"),model(history,AnalysisFilter(context="Competition")).comparisons.keys)
        assertTrue(model(history,AnalysisFilter(target="15")).comparisons.isEmpty())
        assertEquals(3,statistics(history,"me").wins) // Third-party win is never personal.
    }
    @Test fun invalidDeletedUnknownAndLinkedResultsDoNotInflateEvidence() {
        val valid=bout(1)
        val rows=listOf(valid,valid.copy(links=listOf(Origin("import"))),bout(2).copy(deleted=true),bout(3).copy(origin=Origin(uncertain=true)),bout(4,against=null),bout(5,forScore=0,against=0),bout(6,b="unknown"),bout(7).copy(status="Abandoned"),bout(8).copy(stamp=Stamp(day="")))
        assertEquals(setOf("b1"),model(rows).comparisons.keys)
        assertEquals(1,model(rows).ratings.getValue("Foil" to "me").bouts)
        assertEquals(2,model(listOf(valid,bout(2))).ratings.getValue("Foil" to "me").bouts)
        assertTrue(model(listOf(valid.copy(deleted=true))).ratings.isEmpty())
    }
    @Test fun dailySeriesRespectScoresOutcomesScalesAndDateBases() {
        val first=bout(1,a="a",b="me",forScore=2,against=5).copy(detailsB=ParticipantDetails(rating=4.0))
        val second=bout(2,day=1,forScore=null,against=null).copy(outcome="B",detailsA=ParticipantDetails(rating=8.0,ratingScale="other"))
        val later=bout(3,day=3).copy(origin=Origin(dateBasis="calendar"))
        val rows=listOf(first,second,later)
        val points=performancePoints(rows,"me",PerformanceMeasure.POINTS)
        assertEquals(4,points.size)
        assertEquals(5.0,points.first {it.day.endsWith("01") && it.series=="Points for"}.value,0.0)
        assertEquals(50.0,performancePoints(rows,"me",PerformanceMeasure.WIN_RATE).first().value,0.0)
        assertEquals(4.0,performancePoints(rows,"me",PerformanceMeasure.EXECUTION).single().value,0.0)
        assertEquals(listOf("2026-10-01","2026-10-03"),performancePoints(rows,"me",PerformanceMeasure.TOUCH_SHARE).map {it.day})
        assertTrue(performancePoints(emptyList(),"me",PerformanceMeasure.POINTS).isEmpty())
    }
    @Test fun matchingUsesExactPriorDateAndSeparatesBasesAndRevisions() {
        val p=PerformancePoint("2026-10-03","tracking",60.0,2,"Touch share","%")
        val life=listOf(DailyValue("2026-10-01",1.0,"tracking","old","daily",1),DailyValue("2026-10-02",2.0,"calendar","old","daily",1),DailyValue("2026-10-02",3.0,"tracking","new","daily",1))
        val matched=matchPerformance(listOf(p),life,true).single()
        assertEquals("2026-10-02",matched.lifeDay);assertEquals("new",matched.scale);assertEquals(3.0,matched.lifeValue,0.0)
        assertTrue(matchPerformance(listOf(p),life,false).isEmpty())
        assertTrue(matchPerformance(listOf(p),life.take(1),true).isEmpty())
        assertEquals(60.0,matchPerformance(listOf(p),life,true).single().performance,0.0)
    }
}
