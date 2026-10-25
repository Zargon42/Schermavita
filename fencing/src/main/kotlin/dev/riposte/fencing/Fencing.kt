package dev.riposte.fencing

import dev.riposte.core.*
import kotlinx.serialization.Serializable

@Serializable data class Fencer(val id: String = newId(), val name: String, val isMe: Boolean = false,
    val aliases: List<String> = emptyList(), val club: String = "", val handedness: String = "", val notes: String = "", val identityKnown: Boolean = true)
@Serializable data class Session(val id: String = newId(), val name: String, val stamp: Stamp,
    val context: String = "Training", val venue: String = "", val weapon: String = "Foil", val target: Int = 15,
    val sword: String = "", val glove: String = "", val focus: String = "", val notes: String = "", val origin: Origin = Origin(),
    val preparation: Map<String, TrackerAnswer> = emptyMap(), val lenses: Boolean? = null,
    val equipment: Map<String,String> = emptyMap())
@Serializable data class Lesson(val id: String = newId(), val sessionId: String, val coach: String = "",
    val focus: String, val notes: String = "") {
    fun validate() { require(focus.isNotBlank()) { "Enter a lesson focus." } }
}
@Serializable data class ParticipantDetails(val sword: String = "", val glove: String = "", val rating: Double? = null,
    val ratingScale: String = "execution-0-5", val strengths: List<String> = emptyList(), val improve: List<String> = emptyList(), val notes: String = "", val lenses: Boolean? = null, val equipment: Map<String,String> = emptyMap())
fun Session.participantEquipment() = ParticipantDetails(sword=sword,glove=glove,lenses=lenses,equipment=equipment.toMap())
@Serializable data class Bout(val id: String = newId(), val sessionId: String, val a: String, val b: String,
    val scoreA: Int?, val scoreB: Int?, val outcome: String = "auto", val status: String = "Finished",
    val target: Int? = 15, val stamp: Stamp, val detailsA: ParticipantDetails = ParticipantDetails(),
    val detailsB: ParticipantDetails = ParticipantDetails(), val round: String = "", val order: Int? = null,
    val notes: String = "", val origin: Origin = Origin(), val links: List<Origin> = emptyList(), val deleted: Boolean = false) {
    fun winner(): String? = when {
        status != "Finished" -> null
        outcome == "A" -> a
        outcome == "B" -> b
        outcome == "Draw" || outcome == "Unknown" -> null
        scoreA != null && scoreB != null && scoreA > scoreB -> a
        scoreA != null && scoreB != null && scoreB > scoreA -> b
        else -> null
    }
    fun contains(fencer: String) = a == fencer || b == fencer
    fun validate() {
        require(a != b && a.isNotBlank() && b.isNotBlank()) { "Choose two different fencers." }
        require(target == null || target in 1..999) { "Target must be between 1 and 999." }
        require(scoreA == null || scoreA in 0..999) { "Check the first score." }
        require(scoreB == null || scoreB in 0..999) { "Check the second score." }
        require(outcome in listOf("auto","A","B","Draw","Unknown"))
        if (status == "Finished" && outcome == "auto") require(scoreA != null && scoreB != null) { "Enter both scores or an explicit outcome." }
        listOf(detailsA,detailsB).forEach { if (it.ratingScale == "execution-0-5") require(it.rating == null || (it.rating in 0.0..5.0 && it.rating * 2 % 1.0 == 0.0)) }
    }
}
data class FencingStats(val bouts: Int, val wins: Int, val losses: Int, val touchesFor: Int, val touchesAgainst: Int, val rated: Int, val execution: Double?) {
    val winRate: Double? get() = if(wins+losses>0) wins.toDouble()/(wins+losses) else null
    val touchShare: Double? get() = if(touchesFor+touchesAgainst>0) touchesFor.toDouble()/(touchesFor+touchesAgainst) else null
    val winLoss: String get() = if(wins+losses==0) "—" else "$wins : $losses"
    val pointRatio: String get() = if(touchesFor+touchesAgainst>0) "$touchesFor : $touchesAgainst" else "—"
}
fun statistics(bouts: List<Bout>, fencer: String): FencingStats {
    val mine=bouts.filter { !it.deleted && it.contains(fencer) }
    val finished=mine.filter { it.status == "Finished" }
    val scores=finished.filter { it.scoreA != null && it.scoreB != null }
    val ratings=mine.map { if(it.a==fencer) it.detailsA else it.detailsB }.filter { it.ratingScale=="execution-0-5" }.mapNotNull { it.rating }
    return FencingStats(mine.size,finished.count { it.winner()==fencer },finished.count { it.winner()!=null && it.winner()!=fencer },
        scores.sumOf { (if(it.a==fencer) it.scoreA else it.scoreB) ?: 0 },scores.sumOf { (if(it.a==fencer) it.scoreB else it.scoreA) ?: 0 },ratings.size,ratings.takeIf { it.isNotEmpty() }?.average())
}
fun normalizedName(name: String) = name.trim().replace(Regex("\\s+")," ").lowercase(java.util.Locale.ROOT)
interface FencingRepository {
    suspend fun saveSession(session: Session)
    suspend fun saveBout(bout: Bout)
    suspend fun saveFencer(fencer: Fencer)
}
