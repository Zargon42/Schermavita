package dev.riposte.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import dev.riposte.core.*
import dev.riposte.fencing.*

@Composable fun ProcessEditor(d: ParticipantDetails,goals: List<SessionGoal>,onChange: (ParticipantDetails)->Unit) {
    Text("Plan/process execution · ${decimal(d.process)} / 5")
    Slider(d.process?.toFloat() ?: 2.5f,{onChange(d.copy(process=(kotlin.math.round(it*2)/2).toDouble()))},valueRange=0f..5f,steps=9)
    TextButton(onClick={onChange(d.copy(process=null))}) {Text("Leave process unrated")}
    (d.goalAnswers.map {SessionGoal(it.id,it.text)}+goals).distinctBy {it.id}.forEach {goal->
        Text(goal.text)
        val answer=d.goalAnswers.find {it.id==goal.id}
        Chips(listOf("Yes","No","Unknown"),when(answer?.applied){true->"Yes";false->"No";else->"Unknown"},{value->
            onChange(d.copy(goalAnswers=d.goalAnswers.filterNot {it.id==goal.id}+GoalAnswer(goal.id,answer?.text ?: goal.text,when(value){"Yes"->true;"No"->false;else->null})))
        })
    }
}

@Composable fun ReflectionForm(repo: AppRepository,s: Snapshot,session: Session,run: (suspend ()->Unit)->Unit,onClose: ()->Unit,closing: Boolean=true) {
    var draft by remember(session.id) {mutableStateOf(session.reflection ?: SessionReflection(answers=s.reflectionQuestions.filterNot {it.archived}.map {ReflectionAnswer(it)}))}
    fun update(next: SessionReflection) {draft=next;run {repo.change {current->current.copy(sessions=current.sessions.map {if(it.id==session.id) it.copy(reflection=next.copy(status="Draft")) else it})}}}
    fun finish(status: String) {run {repo.change {current->current.copy(sessions=current.sessions.map {if(it.id==session.id) it.copy(reflection=draft.copy(status=status)) else it},settings=if(closing && current.settings.activeSession==session.id) current.settings.copy(activeSession="") else current.settings)};onClose()}}
    Form("Session reflection",onClose,{finish("Complete")}) {
        session.sessionGoals().forEach {Text("Goal: ${it.text}")}
        Input("Reflection notes",draft.notes,{update(draft.copy(notes=it))},lines=4)
        draft.answers.forEachIndexed {index,answer->
            Text(answer.question.title)
            val change: (String)->Unit={value->update(draft.copy(answers=draft.answers.mapIndexed {i,a->if(i==index) a.copy(value=value) else a}))}
            when(answer.question.kind) {
                "Yes/no"->Chips(listOf("Yes","No","Unknown"),answer.value,change)
                "Choice"->Chips(answer.question.options,answer.value,change)
                "Rating"->Chips((0..5).map {it.toString()},answer.value,change)
                else->Input("Answer",answer.value,change,lines=3)
            }
        }
        Button(onClick={finish("Complete")}) {Text(if(closing) "Save and close" else "Save reflection")}
        if(closing) TextButton(onClick={finish("Skipped")}) {Text("Skip")}
        TextButton(onClick=onClose) {Text("Cancel")}
    }
}

@Composable fun QuestionEditor(repo: AppRepository,s: Snapshot,run: (suspend ()->Unit)->Unit,onClose: ()->Unit) {
    var rows by remember {mutableStateOf(s.reflectionQuestions)}
    Form("Recurring reflection questions",onClose,{run {repo.change {it.copy(reflectionQuestions=rows)};onClose()}}) {
        rows.forEachIndexed {index,q->
            fun change(next: ReflectionQuestion) {rows=rows.map {if(it.id==q.id) next else it}}
            Panel("Question ${index+1}") {
                Input("Question title",q.title,{change(q.copy(title=it))})
                Chips(listOf("Text","Rating","Yes/no","Choice"),q.kind,{change(q.copy(kind=it))})
                if(q.kind=="Rating") Hint("Rating 0–5; unanswered is unknown")
                if(q.kind=="Choice") Input("Options, separated by commas",q.options.joinToString(","),{change(q.copy(options=it.split(',').map {v->v.trim()}))})
                Row {TextButton(onClick={if(index>0) rows=rows.toMutableList().also {it[index]=it[index-1];it[index-1]=q}}) {Text("Move up")}
                    TextButton(onClick={if(index<rows.lastIndex) rows=rows.toMutableList().also {it[index]=it[index+1];it[index+1]=q}}) {Text("Move down")}}
                Row {Checkbox(q.archived,{change(q.copy(archived=it))});Text("Archived")}
            }
        }
        Button(onClick={rows=rows+ReflectionQuestion(title="New question")}) {Text("Add question")}
    }
}

@Composable fun SessionJournal(s: Snapshot,onClose: ()->Unit,onReflection: (Session)->Unit) {
    var search by remember {mutableStateOf("")};var selected by remember {mutableStateOf<String?>(null)}
    Form("Session journal",onClose) {
        Input("Search goals, notes and reflections",search,{search=it})
        s.sessions.sortedByDescending {it.stamp.day}.filter {session->listOf(session.name,session.notes,session.focus,session.sessionGoals().joinToString {it.text},session.reflection?.notes.orEmpty(),session.reflection?.answers?.joinToString {it.question.title+" "+it.value}.orEmpty()).any {it.contains(search,true)}}.forEach {session->
            Panel("${session.stamp.day} · ${session.name}") {
                session.sessionGoals().forEach {Text(it.text)}
                if(session.notes.isNotBlank()) Text(session.notes)
                Hint("Reflection: ${session.reflection?.status ?: "Not recorded"}")
                session.reflection?.let {Text(it.notes)}
                TextButton(onClick={selected=if(selected==session.id) null else session.id}) {Text("Session details")}
                TextButton(onClick={onReflection(session)}) {Text("Edit reflection")}
                if(selected==session.id) {
                    session.preparation.values.forEach {Hint("${it.definition.name}: ${it.value}")}
                    s.lessons.filter {it.sessionId==session.id}.forEach {Text("${it.focus}: ${it.notes}")}
                    session.reflection?.answers?.forEach {Text("${it.question.title}: ${it.value.ifBlank {"Unanswered"}}")}
                    val bouts=s.bouts.filter {!it.deleted && it.sessionId==session.id};StatsPanel(bouts,s.me)
                    bouts.forEach {BoutRow(s,it) {}}
                }
            }
        }
    }
}
