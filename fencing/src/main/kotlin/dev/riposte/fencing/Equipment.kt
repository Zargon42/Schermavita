package dev.riposte.fencing

import dev.riposte.core.*
import kotlinx.serialization.Serializable

@Serializable data class EquipmentProfile(
    val id: String = newId(), val owner: String = "me", val kind: String = "Foil", val name: String,
    val aliases: List<String> = emptyList(), val blade: String = "", val handle: String = "",
    val angle: String = "", val brand: String = "", val size: String = "", val firstUsed: String = "",
    val notes: String = "", val retired: Boolean = false, val properties: Map<String,String> = emptyMap()
)
@Serializable data class SessionGoal(val id: String = newId(), val text: String)
@Serializable data class GoalAnswer(val id: String, val text: String, val applied: Boolean? = null)
@Serializable data class ReflectionQuestion(val id: String = newId(), val title: String,
    val kind: String = "Text", val options: List<String> = emptyList(), val archived: Boolean = false)
@Serializable data class ReflectionAnswer(val question: ReflectionQuestion, val value: String = "")
@Serializable data class SessionReflection(val notes: String = "", val answers: List<ReflectionAnswer> = emptyList(),
    val status: String = "Draft")
fun Session.sessionGoals(): List<SessionGoal> = if(goalsConfigured) goals else goals.ifEmpty {
    if(focus.isBlank()) emptyList() else listOf(SessionGoal("legacy:$id",focus))
}
fun ParticipantDetails.withGoalSnapshots(goals: List<SessionGoal>): ParticipantDetails = copy(
    goalAnswers=goalAnswers+goals.filter {goal->goalAnswers.none {it.id==goal.id}}.map {GoalAnswer(it.id,it.text)}
)
