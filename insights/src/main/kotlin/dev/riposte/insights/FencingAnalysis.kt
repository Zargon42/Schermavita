package dev.riposte.insights

import dev.riposte.fencing.*
import dev.riposte.life.*
import java.time.LocalDate
import kotlin.math.pow

data class AnalysisFilter(val weapon: String = "All", val target: String = "All", val context: String = "All")
data class RatingEvidence(val rating: Double = 1500.0, val bouts: Int = 0)
data class BaselineComparison(val boutId: String, val day: String, val weapon: String,
    val a: String, val b: String, val expectedA: Double, val actualA: Double, val touches: Int,
    val priorA: RatingEvidence, val priorB: RatingEvidence) {
    val provisional get() = priorA.bouts < 5 || priorB.bouts < 5
    fun expected(person: String) = if(person == a) expectedA else 1.0-expectedA
    fun actual(person: String) = if(person == a) actualA else 1.0-actualA
}
data class StrengthAnalysis(val comparisons: Map<String,BaselineComparison> = emptyMap(),
    val ratings: Map<Pair<String,String>,RatingEvidence> = emptyMap())

fun validDay(day: String): Boolean = runCatching { LocalDate.parse(day) }.isSuccess
fun analysisBouts(bouts: List<Bout>, sessions: List<Session>, filter: AnalysisFilter): List<Bout> {
    val byId=sessions.associateBy {it.id}
    return bouts.filter { b ->
        val session=byId[b.sessionId]
        !b.deleted && !b.origin.uncertain && validDay(b.stamp.day) && session!=null &&
            (filter.weapon=="All" || session.weapon==filter.weapon) &&
            (filter.target=="All" || b.target?.toString()==filter.target) &&
            (filter.context=="All" || session.context==filter.context)
    }
}

/** Date-only sources have no reliable within-day order. All expectations precede that day's updates. */
fun opponentStrength(bouts: List<Bout>, sessions: List<Session>, fencers: List<Fencer>, filter: AnalysisFilter = AnalysisFilter()): StrengthAnalysis {
    val known=fencers.filter {it.identityKnown}.map {it.id}.toSet()
    val sessionById=sessions.associateBy {it.id}
    val eligible=analysisBouts(bouts,sessions,filter).filter {
        it.status=="Finished" && it.a!=it.b && it.a in known && it.b in known &&
            it.scoreA!=null && it.scoreB!=null && it.scoreA!!>=0 && it.scoreB!!>=0 && it.scoreA!!+it.scoreB!!>0
    }.distinctBy {it.id}
    val ratings=mutableMapOf<Pair<String,String>,RatingEvidence>()
    val comparisons=mutableMapOf<String,BaselineComparison>()
    eligible.groupBy {it.stamp.day}.toSortedMap().forEach { (day,rows) ->
        val updates=mutableMapOf<Pair<String,String>,MutableList<Double>>()
        rows.sortedBy {it.id}.forEach {b ->
            val weapon=sessionById.getValue(b.sessionId).weapon
            val ka=weapon to b.a; val kb=weapon to b.b
            val a=ratings[ka] ?: RatingEvidence(); val other=ratings[kb] ?: RatingEvidence()
            val expected=1.0/(1.0+10.0.pow((other.rating-a.rating)/400.0))
            val total=b.scoreA!!+b.scoreB!!; val actual=b.scoreA!!.toDouble()/total
            comparisons[b.id]=BaselineComparison(b.id,day,weapon,b.a,b.b,expected,actual,total,a,other)
            val delta=32.0*(actual-expected)
            updates.getOrPut(ka) {mutableListOf()}.add(delta)
            updates.getOrPut(kb) {mutableListOf()}.add(-delta)
        }
        updates.forEach { (key,deltas) ->
            val old=ratings[key] ?: RatingEvidence()
            ratings[key]=RatingEvidence(old.rating+deltas.average(),old.bouts+deltas.size)
        }
    }
    return StrengthAnalysis(comparisons,ratings)
}

enum class PerformanceMeasure(val label: String, val unit: String) {
    TOUCH_SHARE("Touch share","%"), POINTS("Points for / against","points"),
    RESULTS("Wins / losses","bouts"), WIN_RATE("Win rate","%"),
    EXECUTION("Self-evaluation","/ 5"), ADJUSTED("Opponent-adjusted touch share","percentage points")
}
data class PerformancePoint(val day: String, val basis: String, val value: Double,
    val count: Int, val series: String, val unit: String, val excluded: Int = 0)

fun performancePoints(bouts: List<Bout>, me: String, measure: PerformanceMeasure,
    strength: StrengthAnalysis = StrengthAnalysis()): List<PerformancePoint> = bouts
    .filter {!it.deleted && !it.origin.uncertain && it.contains(me) && validDay(it.stamp.day)}
    .groupBy {it.stamp.day to it.origin.dateBasis}.toSortedMap(compareBy<Pair<String,String>> {it.first}.thenBy {it.second})
    .flatMap { (key,rows) ->
        val stats=statistics(rows,me)
        val scored=rows.count {it.status=="Finished" && it.scoreA!=null && it.scoreB!=null}
        fun point(value: Double?, count: Int, series: String = measure.label, excluded: Int = 0) =
            value?.let {PerformancePoint(key.first,key.second,it,count,series,measure.unit,excluded)}
        when(measure) {
            PerformanceMeasure.TOUCH_SHARE -> listOfNotNull(point(stats.touchShare?.times(100),scored))
            PerformanceMeasure.POINTS -> if(scored==0) emptyList() else listOfNotNull(point(stats.touchesFor.toDouble(),scored,"Points for"),point(stats.touchesAgainst.toDouble(),scored,"Points against"))
            PerformanceMeasure.RESULTS -> if(stats.wins+stats.losses==0) emptyList() else listOfNotNull(point(stats.wins.toDouble(),stats.wins+stats.losses,"Wins"),point(stats.losses.toDouble(),stats.wins+stats.losses,"Losses"))
            PerformanceMeasure.WIN_RATE -> listOfNotNull(point(stats.winRate?.times(100),stats.wins+stats.losses))
            PerformanceMeasure.EXECUTION -> listOfNotNull(point(stats.execution,stats.rated))
            PerformanceMeasure.ADJUSTED -> {
                val eligible=rows.mapNotNull {strength.comparisons[it.id]}.filterNot {it.provisional}
                val total=eligible.sumOf {it.touches}
                listOfNotNull(point(if(total==0) null else eligible.sumOf {(it.actual(me)-it.expected(me))*it.touches}*100/total,eligible.size,excluded=rows.size-eligible.size))
            }
        }
    }

data class MatchedSample(val fencingDay: String, val lifeDay: String, val basis: String,
    val scale: String, val series: String, val lifeValue: Double, val performance: Double, val bouts: Int)
fun matchPerformance(points: List<PerformancePoint>, life: List<DailyValue>, preceding: Boolean): List<MatchedSample> {
    val indexed=life.groupBy {it.day to it.basis}
    return points.flatMap {p ->
        val day=LocalDate.parse(p.day).minusDays(if(preceding) 1 else 0).toString()
        indexed[day to p.basis].orEmpty().map {v -> MatchedSample(p.day,day,p.basis,v.scale,p.series,v.value,p.value,p.count)}
    }
}
