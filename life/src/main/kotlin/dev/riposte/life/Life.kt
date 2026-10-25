package dev.riposte.life
import dev.riposte.core.*
import kotlinx.serialization.Serializable

@Serializable data class Metric(val id: String = newId(), val name: String, val kind: String = "Rating", val unit: String = "",
    val min: Double = 1.0, val max: Double = 5.0, val scale: String = "rating-1-5", val hidden: Boolean = false, val order: Int = 0,
    val choices: List<String> = emptyList(), val symptom: Boolean = false, val levels: List<RatingLevel> = emptyList(),
    val revisions: List<TrackerRevision> = emptyList())
@Serializable data class Observation(val id: String = newId(), val metric: String, val stamp: Stamp, val value: Double? = null,
    val text: String = "", val scope: String = "momentary", val unit: String = "", val scale: String = "",
    val origin: Origin = Origin(), val deleted: Boolean = false, val endAt: String? = null)
@Serializable data class DailyReview(val id: String, val day: String, val completedAt: String, val notes: String = "")
fun defaultMetrics(): List<Metric> {
    val ratings=listOf("Mood","Stress","Energy","Productivity","Sleep quality").map { Metric(it.lowercase().replace(' ','_'),it) }
    val rest=listOf(Metric("social","Social contact","Yes/no",min=0.0,max=1.0,scale="boolean"), Metric("exercise","Exercise","Duration","min",0.0,1440.0,"minutes"),
        Metric("caffeine","Caffeine","Counter","servings",0.0,30.0,"servings"),Metric("notes","Notes","Text",scale="text"),
        Metric("steps","Steps","Number","steps",0.0,200000.0,"steps",hidden=true),Metric("sleep","Sleep duration","Duration","hours",0.0,24.0,"hours",hidden=true))
    val symptoms=listOf("Headache","Neck ache","Fatigue","Sneezing","Congestion","Cough","Digestive symptoms","Skin / eyes").map { Metric("symptom_"+it.lowercase().replace(Regex("[^a-z]+"),"_"),it,"Rating",min=0.0,max=3.0,scale="severity-0-3",symptom=true) }
    return (ratings+rest+symptoms).mapIndexed { i,m->m.copy(order=i) }
}
data class DailyValue(val day: String, val value: Double, val basis: String, val scale: String, val scope: String, val count: Int)
fun chartableMetrics(metrics: List<Metric>): List<Metric> =
    (metrics + if(metrics.none {it.id=="screen_time"}) listOf(Metric("screen_time","Screen time","Duration","min",0.0,2880.0,"unlocked-screen-minutes",hidden=true)) else emptyList())
        .filter {it.kind !in listOf("Text","Tag")}
fun dailyValues(events: List<Observation>, metric: Metric): List<DailyValue> = events.filter { !it.deleted && it.metric==metric.id && it.value!=null && !it.origin.uncertain }
    .groupBy { Triple(it.stamp.day,it.origin.dateBasis,it.scale) }.map { (key,rows)->
        val preferred=rows.filter { it.scope=="daily" }.ifEmpty { rows.filter { it.scope=="aggregate" } }.ifEmpty { rows }
        val v=when { preferred.any { it.scope=="daily" || it.scope=="aggregate" } -> preferred.maxWith(compareBy<Observation> {it.stamp.createdAt}.thenBy {it.id}).value!!
            metric.kind in listOf("Counter","Duration") -> preferred.sumOf { it.value!! }
            metric.kind=="Yes/no" || metric.symptom -> preferred.maxOf { it.value!! }
            else -> preferred.map { it.value!! }.average() }
        DailyValue(key.first,v,key.second,key.third,preferred.first().scope,preferred.size)
    }.sortedBy { it.day }

fun Metric.definition() = TrackerRevision(scale,name,kind,unit,min,max,
    if(kind=="Rating") levels.ifEmpty { (min.toInt()..max.toInt()).map { RatingLevel(it.toDouble(),
        if(symptom && min==0.0 && max==3.0) listOf("None","Mild","Moderate","Severe")[it] else it.toString()) } } else emptyList(),choices)
fun Metric.atScale(id: String): Metric {
    if(id==scale || id.isBlank()) return this
    val d=revisions.find {it.id==id} ?: return copy(scale=id,kind="Number",min=-Double.MAX_VALUE,max=Double.MAX_VALUE)
    return copy(name=d.name,kind=d.kind,unit=d.unit,min=d.min,max=d.max,scale=d.id,levels=d.levels,choices=d.choices)
}
fun Metric.edited(d: TrackerRevision): Metric {
    d.validate()
    require(d.kind==kind) { "Tracker type cannot change." }
    val old=definition()
    val changed=old.copy(id=d.id,name=d.name)!=d
    return copy(name=d.name,unit=d.unit,min=d.min,max=d.max,levels=d.levels,choices=d.choices,
        scale=if(changed) newId() else scale,
        revisions=if(changed) (revisions+old).distinctBy {it.id} else revisions)
}
