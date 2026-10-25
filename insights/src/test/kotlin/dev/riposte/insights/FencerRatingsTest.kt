package dev.riposte.insights

import dev.riposte.core.Stamp
import dev.riposte.fencing.*
import org.junit.Assert.*
import org.junit.Test

class FencerRatingsTest {
    private val people=listOf(Fencer("me","Me",true),Fencer("a","Alice"),Fencer("b","Bob"),Fencer("c","Cara",aliases=listOf("Rocket")),Fencer("d","Dan"))
    private val strength=StrengthAnalysis(ratings=mapOf(("Foil" to "a") to RatingEvidence(1516.0,1),
        ("Foil" to "b") to RatingEvidence(1484.0,5),("Foil" to "c") to RatingEvidence(1516.0,8),
        ("Épée" to "b") to RatingEvidence(1600.0,10)))
    @Test fun ratingOrdersKeepMissingLastAndTiesStableAcrossWeapons() {
        fun sorted(order: FencerOrder,weapon: String="Foil")=rankedFencers(people.reversed(),emptyList(),strength,weapon,order).map {it.fencer.id}
        assertEquals(listOf("a","c","b","d"),sorted(FencerOrder.HIGHEST))
        assertEquals(listOf("b","a","c","d"),sorted(FencerOrder.LOWEST))
        assertEquals(listOf("b","a","c","d"),sorted(FencerOrder.HIGHEST,"Épée"))
        assertEquals(listOf("a","b","c","d"),sorted(FencerOrder.NAME))
        val provisional=rankedFencers(people,emptyList(),strength,"Foil",FencerOrder.HIGHEST).first()
        assertEquals(1,provisional.evidence!!.bouts)
    }
    @Test fun latestEvidenceRecalculatesAndCountsExcludeDeletedBouts() {
        val session=Session("s","Club",Stamp(day="2026-10-01"))
        val first=Bout("one","s","a","b",5,0,stamp=Stamp(day="2026-10-01"))
        val second=first.copy(id="two",scoreA=0,scoreB=5,stamp=Stamp(day="2026-10-02"))
        fun rows(bouts: List<Bout>)=rankedFencers(people,bouts,opponentStrength(bouts,listOf(session),people),"Foil",FencerOrder.HIGHEST)
        assertEquals(1516.0,rows(listOf(first)).first().evidence!!.rating,0.0)
        assertEquals("b",rows(listOf(first,second)).first().fencer.id)
        assertEquals("a",rows(listOf(first,second.copy(deleted=true))).first().fencer.id)
        assertEquals(1,rows(listOf(first,second.copy(deleted=true))).first().bouts)
        assertEquals(listOf("c"),rankedFencers(people,emptyList(),strength,"Foil",FencerOrder.BOUTS,"rocket").map {it.fencer.id})
    }
}
