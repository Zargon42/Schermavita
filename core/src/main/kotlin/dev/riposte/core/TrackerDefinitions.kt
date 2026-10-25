package dev.riposte.core

import kotlinx.serialization.Serializable

@Serializable data class RatingLevel(val value: Double, val label: String)
@Serializable data class TrackerRevision(
    val id: String, val name: String, val kind: String, val unit: String = "",
    val min: Double = 0.0, val max: Double = 5.0,
    val levels: List<RatingLevel> = emptyList(), val choices: List<String> = emptyList()
) {
    fun validate() {
        require(name.isNotBlank()) { "Enter a tracker name." }
        require(min.isFinite() && max.isFinite() && min <= max) { "Check the numeric range." }
        if(kind == "Rating") {
            require(levels.isNotEmpty()) { "Add at least one rating level." }
            require(levels.all { it.value.isFinite() && it.label.isNotBlank() }) { "Every level needs a value and label." }
            require(levels.zipWithNext().all { (a,b) -> a.value < b.value }) { "Rating values must increase." }
        }
        require(choices.distinct().size == choices.size && choices.none { it.isBlank() }) { "Choices must be unique and nonblank." }
    }
}
@Serializable data class TrackerAnswer(val value: String, val definition: TrackerRevision)
@Serializable data class EquipmentCategory(val id: String = newId(), val name: String, val archived: Boolean = false)
fun defaultSessionTrackers() = listOf(
    TrackerRevision("willingness-1-5", "Willingness to fence", "Rating", min=1.0, max=5.0,
        levels=(1..5).map { RatingLevel(it.toDouble(), when(it) {1->"Not at all";5->"Very keen";else->it.toString()}) }),
    TrackerRevision("warmup-0-2", "Warm-up before first bout", "Rating", min=0.0, max=2.0,
        levels=listOf(RatingLevel(0.0,"None"),RatingLevel(1.0,"Light warm-up"),RatingLevel(2.0,"Full routine"))),
    TrackerRevision("transport-v1", "Arrival transport", "Tag", choices=listOf("Bike","Walk","Car","Public transport","Other"))
)
