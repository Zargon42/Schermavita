package dev.riposte.insights

import dev.riposte.fencing.*
import java.util.Locale

enum class FencerOrder(val label: String) {
    BOUTS("Most bouts"), HIGHEST("Rating: highest first"), LOWEST("Rating: lowest first"), NAME("Name")
}

data class FencerRating(val fencer: Fencer, val bouts: Int, val evidence: RatingEvidence?)

/** Unrated profiles remain discoverable, after rated profiles in either rating order. */
fun rankedFencers(fencers: List<Fencer>, bouts: List<Bout>, strength: StrengthAnalysis,
    weapon: String, order: FencerOrder, search: String = ""): List<FencerRating> {
    val counts=mutableMapOf<String,Int>()
    bouts.filterNot {it.deleted}.forEach {b -> setOf(b.a,b.b).forEach {counts[it]=(counts[it] ?: 0)+1}}
    val rows=fencers.filter {!it.isMe && (it.name.contains(search,true) || it.aliases.any {a->a.contains(search,true)})}
        .map {FencerRating(it,counts[it.id] ?: 0,strength.ratings[weapon to it.id])}
    val primary=when(order) {
        FencerOrder.BOUTS -> compareByDescending<FencerRating> {it.bouts}
        FencerOrder.NAME -> compareBy {_: FencerRating -> 0}
        FencerOrder.HIGHEST -> compareBy<FencerRating> {it.evidence==null}.thenByDescending {it.evidence?.rating}
        FencerOrder.LOWEST -> compareBy<FencerRating> {it.evidence==null}.thenBy {it.evidence?.rating}
    }
    return rows.sortedWith(primary.thenBy {it.fencer.name.lowercase(Locale.ROOT)}.thenBy {it.fencer.id})
}
