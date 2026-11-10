package dev.riposte.life

fun Metric.compatibleScale(id: String): String {
    if(id!=scale && revisions.none {it.id==id}) return "unknown:$id"
    val d=atScale(id)
    return (listOf(scale)+revisions.map {it.id}).first {candidate->val other=atScale(candidate)
        other.kind==d.kind && other.min==d.min && other.max==d.max && other.unit==d.unit
    }
}

fun compatibleDailyValues(events: List<Observation>,metric: Metric,combined: Boolean,scales: Set<String>,bases: Set<String>): List<DailyValue> {
    val eligible=events.filter {!it.deleted && !it.origin.uncertain && it.metric==metric.id && it.value!=null && it.scale in scales && it.origin.dateBasis in bases}
    return eligible.groupBy {Triple(it.stamp.day,it.origin.dateBasis,if(combined) metric.compatibleScale(it.scale) else it.scale)}.flatMap {(_,rows)->
        val preferred=rows.filter {it.scope=="daily"}.ifEmpty {rows.filter {it.scope=="aggregate"}}.ifEmpty {rows}
        val latest=preferred.maxWith(compareBy<Observation> {it.stamp.createdAt}.thenBy {it.id})
        val canonical=if(combined) metric.compatibleScale(latest.scale) else latest.scale
        val selected=if(preferred.any {it.scope in listOf("daily","aggregate")}) listOf(latest) else preferred
        dailyValues(selected.map {it.copy(scale=canonical)},metric).map {it.copy(revision=selected.map {r->r.scale}.distinct().sorted().joinToString(" + "))}
    }.sortedBy {it.day}
}

data class RollingPoint(val day: Double,val value: Double,val count: Int)
fun rollingMean(points: List<Pair<Double,Double>>,days: Int): List<RollingPoint> {
    require(days>0)
    val daily=points.groupBy {it.first}.toSortedMap().map {it.key to it.value.map {p->p.second}.average()}
    return daily.mapNotNull {point->val window=daily.filter {it.first<=point.first && it.first>point.first-days};
        if(window.size<2) null else RollingPoint(point.first,window.map {it.second}.average(),window.size)}
}
