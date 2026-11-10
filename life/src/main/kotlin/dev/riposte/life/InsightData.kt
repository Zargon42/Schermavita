package dev.riposte.life

import dev.riposte.core.RatingLevel
import dev.riposte.core.TrackerRevision

data class InsightData(val metrics: List<Metric>,val observations: List<Observation>)

/** Analytical alias only: saved tracker IDs, values, definitions and date provenance stay intact. */
fun insightData(metrics: List<Metric>,observations: List<Observation>): InsightData {
    val current=metrics.find {it.id=="mood"}
        ?: metrics.singleOrNull {it.name.equals("Mood (Happiness)",ignoreCase=true)}
        ?: return InsightData(metrics,observations)
    val historical=metrics.find {it.id=="legacy:unified_mood" && it.scale=="rating-1-5"}
        ?: return InsightData(metrics,observations)
    val historicalScale="linked:${historical.id}:${historical.scale}"
    // The history importer explicitly identifies this measurement as rating-1-5,
    // although its generic archive container uses Number with unbounded limits.
    val definition=TrackerRevision(historicalScale,historical.name,"Rating",historical.unit,1.0,5.0,
        (1..5).map {RatingLevel(it.toDouble(),it.toString())})
    val projected=current.copy(revisions=(current.revisions+definition).distinctBy {it.id})
    val linked=observations.map {observation->
        if(observation.metric==historical.id && observation.scale==historical.scale)
            observation.copy(metric=current.id,scale=historicalScale)
        else observation
    }
    // Leave an independently selectable archive tracker if it also contains unknown scales.
    val otherScales=observations.any {it.metric==historical.id && it.scale!=historical.scale}
    return InsightData(metrics.filter {it.id!=historical.id || otherScales}.map {if(it.id==current.id) projected else it},linked)
}
