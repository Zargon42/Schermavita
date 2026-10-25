@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package dev.riposte.app
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.riposte.core.*
import dev.riposte.fencing.*
import dev.riposte.insights.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.*

@Composable fun StatsPanel(bouts: List<Bout>,person: String,title: String="Recorded performance") {
    val stats=statistics(bouts,person)
    Panel(title) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Stat("Bouts",stats.bouts.toString()); Stat("Win rate",stats.winRate?.let { "${(it*100).toInt()}%" } ?: "—"); Stat("Touch share",stats.touchShare?.let { "${(it*100).toInt()}%" } ?: "—")
        }
        Hint("${stats.wins} wins · ${stats.losses} losses · ${stats.touchesFor}–${stats.touchesAgainst} touches")
        if(stats.rated>0) Hint("Execution ${decimal(stats.execution)} / 5 · ${stats.rated} rated bouts")
        Hint("Recorded results only. Win rate excludes unresolved results and draws.")
    }
}
@Composable fun Stat(label: String,value: String) { Column { Text(value,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold); Hint(label) } }
@Composable fun BoutRow(s: Snapshot,b: Bout,onClick: ()->Unit) {
    val session=s.sessions.find { it.id==b.sessionId }
    Card(Modifier.fillMaxWidth().clickable(onClick=onClick)) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(s.name(b.a),modifier=Modifier.weight(1f)); Text("${b.scoreA ?: "?"} : ${b.scoreB ?: "?"}",fontWeight=FontWeight.Bold); Text(s.name(b.b),modifier=Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.End) }
        Hint("${b.stamp.day.ifBlank { "Date unresolved" }} · ${session?.weapon ?: ""} · ${session?.context ?: ""} · ${b.target?.let {"to $it"} ?: "Target unknown"}")
        if(b.origin.uncertain) Hint("Date needs review · excluded from daily comparisons")
        else if(b.origin.inferred) Hint("Date inferred in the original analysis")
        if(b.status!="Finished") Hint(b.status)
    } }
}
@Composable fun FencingScreen(repo: AppRepository,s: Snapshot,run: (suspend ()->Unit)->Unit,onSession: (Session?)->Unit,onBout: (Bout)->Unit,onAdd: ()->Unit,onPerson: (String)->Unit) {
    var search by remember { mutableStateOf("") }; var sessionSearch by remember { mutableStateOf("") }
    val strength=rememberFencerStrength(s)
    var ratingWeapon by remember {mutableStateOf(s.settings.defaultWeapon)}
    var order by remember {mutableStateOf(FencerOrder.BOUTS)}
    var orderMenu by remember {mutableStateOf(false)}
    var shown by remember(search,ratingWeapon,order) {mutableStateOf(20)}
    val active=s.sessions.find { it.id==s.settings.activeSession }
    ScrollPage {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Heading("On the piste"); TextButton(onClick={ onSession(null) }) { Text("New session") } }
        if(active!=null) Panel(active.name) { Text("${active.stamp.day} · ${active.context} · ${active.weapon}"); Hint("${active.venue}  ·  ${active.sword.ifBlank { "No sword set" }} / ${active.glove.ifBlank { "No glove set" }}")
            s.lessons.filter { it.sessionId==active.id }.forEach { lesson->
                Hint("Lesson · ${lesson.focus}${lesson.coach.takeIf {it.isNotBlank()}?.let {" · Coach: $it"}.orEmpty()}")
                if(lesson.notes.isNotBlank()) Hint(lesson.notes)
            }
            Button(onClick=onAdd,modifier=Modifier.fillMaxWidth()) { Text("Add bout") }; TextButton(onClick={onSession(active)}) { Text("Session details & equipment") }
            TextButton(onClick={run {repo.closeSession(active.id)}}) {Text("Close session")}
        }
        else Panel("Start a session") { Text("No active session. Set the details once, then add bouts as you go."); Button(onClick={onSession(null)}) { Text("New session") }; Hint("Tap a saved session below to resume it.") }
        if(active!=null) { val bouts=s.bouts.filter { !it.deleted && it.sessionId==active.id }; StatsPanel(bouts,s.me,"This session · your bouts"); bouts.sortedByDescending { it.stamp.occurredAt }.take(30).forEach { b->BoutRow(s,b) { onBout(b) } } }
        Heading("Fencers")
        Input("Find a fencer",search,{ search=it })
        Hint("Rating weapon")
        Chips(listOf("Foil","Épée","Sabre"),ratingWeapon,{ratingWeapon=it})
        Box {
            OutlinedButton(onClick={orderMenu=true}) {Text("Sort fencers: ${order.label}")}
            DropdownMenu(expanded=orderMenu,onDismissRequest={orderMenu=false}) {
                FencerOrder.entries.forEach {option->DropdownMenuItem(text={Text(option.label)},onClick={order=option;orderMenu=false})}
            }
        }
        Hint("Latest score ratings estimate strength from all recorded results in each weapon. Provisional until 5 eligible bouts; unrated fencers sort last by rating.")
        val people=rankedFencers(s.fencers,s.bouts,strength ?: StrengthAnalysis(),ratingWeapon,order,search)
        people.take(shown).forEach { row-> val f=row.fencer
            ListItem(headlineContent={Text(f.name)},supportingContent={Column {
                Text("${f.club.ifBlank { "Fencer profile" }} · ${row.bouts} bouts")
                Text(ratingLabel(ratingWeapon,row.evidence,strength==null))
            }},modifier=Modifier.clickable { onPerson(f.id) })
        }
        if(people.isEmpty()) Hint("No matching fencers.")
        if(people.size>shown) TextButton(onClick={shown+=20}) {Text("Show more fencers (${people.size-shown} remaining)")}
        Heading("Sessions")
        Input("Search sessions by name or date",sessionSearch,{sessionSearch=it})
        s.sessions.filter { it.name.contains(sessionSearch,true) || it.stamp.day.contains(sessionSearch) }.sortedByDescending { it.stamp.day }.take(30).forEach { session ->
            val lessonCount=s.lessons.count {it.sessionId==session.id}
            ListItem(headlineContent={Text(session.name)},supportingContent={Text("${session.stamp.day} · ${session.context} · ${session.venue}${if(lessonCount>0) " · $lessonCount lesson${if(lessonCount==1) "" else "s"}" else ""}")},trailingContent={TextButton(onClick={onSession(session)}) { Text("Edit") }},modifier=Modifier.clickable { run { repo.change { it.copy(settings=it.settings.copy(activeSession=session.id)) } } })
        }
    }
}
@Composable private fun rememberFencerStrength(s: Snapshot): StrengthAnalysis? {
    val strength by produceState<StrengthAnalysis?>(null,s.bouts,s.sessions,s.fencers) {
        value=null
        value=withContext(Dispatchers.Default) {opponentStrength(s.bouts,s.sessions,s.fencers)}
    }
    return strength
}
private fun ratingLabel(weapon: String,evidence: RatingEvidence?,loading: Boolean=false): String =
    "$weapon score rating: "+when {
        loading -> "Calculating…"
        evidence==null -> "Not rated"
        else -> "${decimal(evidence.rating)} · ${evidence.bouts} eligible "+(if(evidence.bouts==1) "bout" else "bouts")+if(evidence.bouts<5) " · Provisional" else ""
    }
@Composable fun PersonInput(label: String,s: Snapshot,name: String,onName: (String)->Unit,selected: String?,onSelect: (Fencer)->Unit,exclude: String?=null) {
    Input(label,name,onName)
    if(selected==null || name.isBlank()) {
        val options=s.fencers.filter { it.id!=exclude && (name.isBlank() || it.name.contains(name,true) || it.aliases.any { a->a.contains(name,true) }) }.sortedByDescending { f->s.bouts.count { it.contains(f.id) } }.take(6)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { options.forEach { f-> SuggestionChip(onClick={onSelect(f)},label={Text(f.name+if(f.club.isNotBlank()) " · ${f.club}" else "")}) } }
        if(name.isNotBlank() && options.none { normalizedName(it.name)==normalizedName(name) }) Hint("A new profile is created if this name does not match an existing fencer.")
    }
}
@Composable fun SessionForm(s: Snapshot,existing: Session?,onClose: ()->Unit,onSave: (Session,List<EquipmentCategory>,List<Lesson>)->Unit) {
    var preparation by remember {mutableStateOf(existing?.preparation.orEmpty())}
    var lenses by remember {mutableStateOf(existing?.lenses)};var equipment by remember {mutableStateOf(existing?.equipment.orEmpty())}
    var categories by remember {mutableStateOf(s.settings.equipmentCategories)};var manageCategories by remember {mutableStateOf(false)}
    val id=remember { existing?.id ?: newId() }; val initial=remember { existing?.stamp ?: DayClock.stamp(Instant.now(),s.settings,s.days) }
    var lessons by remember(id) {mutableStateOf(s.lessons.filter {it.sessionId==id})}
    var editingLesson by remember {mutableStateOf<Lesson?>(null)};var addingLesson by remember {mutableStateOf(false)}
    var lessonCoach by remember {mutableStateOf("")};var lessonFocus by remember {mutableStateOf("")};var lessonNotes by remember {mutableStateOf("")}
    var lessonError by remember {mutableStateOf("")}
    var name by remember { mutableStateOf(existing?.name ?: "Fencing") }; var context by remember { mutableStateOf(existing?.context ?: "Training") }
    var weapon by remember { mutableStateOf(existing?.weapon ?: "Foil") }; var venue by remember { mutableStateOf(existing?.venue.orEmpty()) }
    var target by remember { mutableStateOf((existing?.target ?: 15).toString()) }; var sword by remember { mutableStateOf(existing?.sword.orEmpty()) }; var glove by remember { mutableStateOf(existing?.glove.orEmpty()) }
    var focus by remember { mutableStateOf(existing?.focus.orEmpty()) }; var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var time by remember { mutableStateOf(localTime(initial)) }; var day by remember { mutableStateOf(initial.day) }; var error by remember { mutableStateOf("") }
    Form(if(existing==null) "New session" else "Session details",onClose,onSave={ try {
        require(name.isNotBlank()); LocalDate.parse(day); val instant=if(time.isBlank() && existing?.origin?.precision!="instant") "" else parsedTime(time,initial.zone).toString(); val number=target.toInt(); require(number in 1..999)
        onSave(Session(id,name,initial.copy(occurredAt=instant,day=day,dayOverride=true),context,venue,weapon,number,sword,glove,focus,notes,existing?.origin ?: Origin(),preparation,lenses,equipment),categories,lessons)
    } catch(e: Exception) { error="Check the name, date, time, and target score." } }) {
        if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
        Input("Session name",name,{name=it}); Chips(listOf("Training","Competition","Camp","Other"),context,{context=it}); Input("Venue",venue,{venue=it})
        Chips(listOf("Foil","Épée","Sabre"),weapon,{weapon=it}); Chips(listOf("5","10","15"),target,{target=it}); Input("Target score",target,{target=it},true)
        Heading("Preparation")
        s.settings.sessionTrackers.forEachIndexed { index,current ->
            val key=preparationKeys[index];val answer=preparation[key];val definition=answer?.definition ?: current
            Text(definition.name);DefinitionInput(definition,answer?.value.orEmpty(),{preparation=preparation+(key to TrackerAnswer(it,definition))})
            TextButton(onClick={preparation=preparation-key}) {Text("Leave unknown")}
            if(answer!=null && definition.id!=current.id) Hint("Uses the scale saved with this session.")
        }
        Heading("Equipment")
        Input("Sword",sword,{sword=it}); Input("Glove",glove,{glove=it})
        LensesInput(lenses,{lenses=it});EquipmentFields(categories,equipment,{equipment=it})
        TextButton(onClick={manageCategories=true}) {Text("Manage equipment categories")}
        Heading("Lessons")
        lessons.forEach { lesson ->
            Panel(lesson.focus) {
                if(lesson.coach.isNotBlank()) Hint("Coach · ${lesson.coach}")
                if(lesson.notes.isNotBlank()) Text(lesson.notes)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick={editingLesson=lesson;addingLesson=false;lessonCoach=lesson.coach;lessonFocus=lesson.focus;lessonNotes=lesson.notes;lessonError=""}) {Text("Edit")}
                    TextButton(onClick={lessons=lessons.filterNot {it.id==lesson.id};if(editingLesson?.id==lesson.id){editingLesson=null;addingLesson=false}}) {Text("Remove")}
                }
            }
        }
        if(addingLesson || editingLesson!=null) {
            Panel(if(editingLesson==null) "Log a lesson" else "Edit lesson") {
                if(lessonError.isNotBlank()) Text(lessonError,color=MaterialTheme.colorScheme.error)
                Input("Coach (optional)",lessonCoach,{lessonCoach=it})
                Input("Lesson focus",lessonFocus,{lessonFocus=it})
                Input("What I learned / notes",lessonNotes,{lessonNotes=it},lines=4)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick={addingLesson=false;editingLesson=null;lessonError=""}) {Text("Cancel")}
                    TextButton(onClick={
                        val updated=Lesson(id=editingLesson?.id ?: newId(),sessionId=id,coach=lessonCoach.trim(),focus=lessonFocus.trim(),notes=lessonNotes.trim())
                        try { updated.validate();lessons=lessons.replace(updated) {id};addingLesson=false;editingLesson=null;lessonError="" }
                        catch(e: IllegalArgumentException) {lessonError=e.message ?: "Check the lesson focus."}
                    }) {Text("Save lesson")}
                }
            }
        } else OutlinedButton(onClick={addingLesson=true;editingLesson=null;lessonCoach="";lessonFocus="";lessonNotes="";lessonError=""},modifier=Modifier.fillMaxWidth()) {Text("Add lesson")}
        Input("Tactical focus",focus,{focus=it}); Input("Notes",notes,{notes=it},lines=3)
        Input("Started · yyyy-MM-dd HH:mm",time,{time=it}); Input("Tracking day · yyyy-MM-dd",day,{day=it})
        Hint("${initial.zone}. Changes to equipment and target apply to future bouts. Existing bouts keep their saved details and day.")
    }
    if(manageCategories) CategoryForm(categories,{manageCategories=false},{categories=it;manageCategories=false})
}
@Composable fun DetailsEditor(title: String,d: ParticipantDetails,tags: List<String>,categories: List<EquipmentCategory>,onChange: (ParticipantDetails)->Unit) {
    Panel(title) {
        Input("Sword",d.sword,{onChange(d.copy(sword=it))}); Input("Glove",d.glove,{onChange(d.copy(glove=it))})
        LensesInput(d.lenses,{onChange(d.copy(lenses=it))});EquipmentFields(categories,d.equipment,{onChange(d.copy(equipment=it))})
        Text("Execution · ${decimal(d.rating)} / 5")
        Slider(d.rating?.toFloat() ?: 2.5f,{onChange(d.copy(rating=(kotlin.math.round(it*2)/2).toDouble()))},valueRange=0f..5f,steps=9)
        TextButton(onClick={onChange(d.copy(rating=null))}) { Text("Leave unrated") }
        Hint("0: could not execute · 5: consistently executed. Optional and separate from the score.")
        Text("Worked well")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) { tags.forEach { t-> FilterChip(t in d.strengths,onClick={onChange(d.copy(strengths=if(t in d.strengths) d.strengths-t else d.strengths+t))},label={Text(t)}) } }
        Text("Work on")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) { tags.forEach { t-> FilterChip(t in d.improve,onClick={onChange(d.copy(improve=if(t in d.improve) d.improve-t else d.improve+t))},label={Text(t)}) } }
        Input("Participant notes",d.notes,{onChange(d.copy(notes=it))},lines=2)
    }
}
@Composable fun BoutForm(s: Snapshot,existing: Bout?,onClose: ()->Unit,onSave: (Bout,List<Fencer>)->Unit,onDelete: (Bout)->Unit) {
    val id=remember { existing?.id ?: newId() }
    var sessionId by remember { mutableStateOf(existing?.sessionId ?: s.settings.activeSession) }; val session=s.sessions.firstOrNull { it.id==sessionId }
    if(session==null) { Form("Add bout",onClose) { Text("Create a session first.") }; return }
    val initial=remember { existing?.stamp ?: DayClock.stamp(Instant.now(),s.settings,s.days,session.stamp.day) }
    var other by remember { mutableStateOf(existing?.let { !it.contains(s.me) } ?: false) }
    var aid by remember { mutableStateOf<String?>(existing?.a ?: s.me) }; var bid by remember { mutableStateOf<String?>(existing?.b) }
    var aname by remember { mutableStateOf(s.name(existing?.a ?: s.me)) }; var bname by remember { mutableStateOf(existing?.let { s.name(it.b) }.orEmpty()) }
    var scoreA by remember { mutableStateOf(existing?.scoreA?.toString().orEmpty()) }; var scoreB by remember { mutableStateOf(existing?.scoreB?.toString().orEmpty()) }
    var target by remember { mutableStateOf(if(existing==null) session.target.toString() else existing.target?.toString().orEmpty()) }
    var outcome by remember { mutableStateOf(existing?.outcome ?: "auto") }; var status by remember { mutableStateOf(existing?.status ?: "Finished") }
    var da by remember(aid) { mutableStateOf(existing?.takeIf { it.a==aid }?.detailsA ?: if(aid==s.me) session.participantEquipment() else ParticipantDetails()) }
    var db by remember(bid) { mutableStateOf(existing?.takeIf { it.b==bid }?.detailsB ?: if(bid==s.me) session.participantEquipment() else ParticipantDetails()) }
    var detail by remember { mutableStateOf(false) }; var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }; var round by remember { mutableStateOf(existing?.round.orEmpty()) }
    var time by remember { mutableStateOf(localTime(initial)) }; var day by remember { mutableStateOf(initial.day) }; var error by remember { mutableStateOf("") }; var sessionSearch by remember { mutableStateOf("") }
    val save={ try {
        val a=resolvePerson(s,aname,aid); val b=resolvePerson(s,bname,bid); LocalDate.parse(day)
        val actual=if(time==localTime(initial)) initial.occurredAt else if(time.isBlank() && existing!=null && existing.origin.precision!="instant") "" else parsedTime(time,initial.zone).toString()
        val targetNumber=target.toIntOrNull();require(targetNumber!=null || existing!=null && target.isBlank()) {"Enter a valid target score."}
        val bout=Bout(id,sessionId,a.id,b.id,scoreA.toIntOrNull(),scoreB.toIntOrNull(),outcome,status,targetNumber,initial.copy(occurredAt=actual,day=day,dayOverride=true),da,db,round,existing?.order ?: s.bouts.count { it.sessionId==sessionId }+1,notes,existing?.origin ?: Origin(),existing?.links.orEmpty())
        bout.validate(); onSave(bout,listOf(a,b))
    } catch(e: Exception) { error=e.message ?: "Check the bout details." }; Unit }
    Form(if(existing==null) "Add bout" else "Edit bout",onClose,save) {
        Hint("${session.name} · ${session.stamp.day} · ${session.weapon}")
        if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
        Chips(listOf("My bout","Other fencers"),if(other) "Other fencers" else "My bout",{ other=it=="Other fencers"; aid=if(other) null else s.me; aname=if(other) "" else s.name(s.me) })
        if(other) PersonInput("Fencer A",s,aname,{aname=it;aid=null},aid,{aid=it.id;aname=it.name},bid) else Text(aname,fontWeight=FontWeight.SemiBold)
        PersonInput(if(other) "Fencer B" else "Opponent",s,bname,{bname=it;bid=null},bid,{bid=it.id;bname=it.name},aid)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { Input(if(other) "A score" else "Your score",scoreA,{scoreA=it},true,modifier=Modifier.weight(1f)); Input(if(other) "B score" else "Opponent score",scoreB,{scoreB=it},true,modifier=Modifier.weight(1f)) }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick={scoreA=target}) {Text(if(other) "A to target" else "Won to target")}; OutlinedButton(onClick={scoreB=target}) {Text(if(other) "B to target" else "Lost to target")} }
        Button(save,Modifier.fillMaxWidth()) { Text("Save bout") }
        TextButton(onClick={detail=!detail}) { Text(if(detail) "Hide extra details" else "Rating, tactics & more") }
        if(detail) {
            Chips(listOf("5","10","15"),target,{target=it}); Input("Target score",target,{target=it},true)
            Chips(listOf("Finished","Unfinished"),status,{status=it}); Hint("Outcome: auto uses the scores; select A or B for a priority decision.")
            Chips(listOf("auto","A","B","Draw","Unknown"),outcome,{outcome=it})
            DetailsEditor(aname.ifBlank { "Fencer A" },da,s.settings.tacticalTags,s.settings.equipmentCategories,{da=it}); if(other || existing?.detailsB!=null && existing.detailsB!=ParticipantDetails()) DetailsEditor(bname.ifBlank { "Fencer B" },db,s.settings.tacticalTags,s.settings.equipmentCategories,{db=it})
            Input("Round / poule",round,{round=it}); Input("Notes",notes,{notes=it},lines=3); Input("Occurred · yyyy-MM-dd HH:mm",time,{time=it}); Input("Tracking day · yyyy-MM-dd",day,{day=it})
            Input("Move to session · search",sessionSearch,{sessionSearch=it})
            s.sessions.filter { it.name.contains(sessionSearch,true) || it.stamp.day.contains(sessionSearch) }.sortedByDescending { it.stamp.day }.take(6).forEach { item -> FilterChip(item.id==sessionId,onClick={sessionId=item.id;day=item.stamp.day},label={Text("${item.name} · ${item.stamp.day}")}) }
        }
        if(existing!=null) TextButton(onClick={onDelete(existing)}) {Text("Delete bout",color=MaterialTheme.colorScheme.error)}
    }
}
@Composable fun ProfileScreen(repo: AppRepository,s: Snapshot,id: String,run: (suspend ()->Unit)->Unit,onBout: (Bout)->Unit) {
    val p=s.fencers.find { it.id==id } ?: return
    val strength=rememberFencerStrength(s)
    var edit by remember { mutableStateOf(false) }; var name by remember(id) { mutableStateOf(p.name) }; var aliases by remember(id) { mutableStateOf(p.aliases.joinToString(", ")) }
    var club by remember(id) { mutableStateOf(p.club) }; var hand by remember(id) { mutableStateOf(p.handedness) }; var notes by remember(id) { mutableStateOf(p.notes) }
    var from by remember { mutableStateOf("") }; var to by remember { mutableStateOf("") }; var weapon by remember { mutableStateOf("All") }; var target by remember { mutableStateOf("All") }; var context by remember { mutableStateOf("All") }; var opponent by remember { mutableStateOf("") }
    var merge by remember { mutableStateOf<Fencer?>(null) }; var mergeSearch by remember { mutableStateOf("") }
    val bouts=s.bouts.filter { b -> !b.deleted && b.contains(id) && (from.isBlank() || b.stamp.day>=from) && (to.isBlank() || b.stamp.day<=to) && (target=="All" || b.target.toString()==target) &&
        s.sessions.any { it.id==b.sessionId && (weapon=="All" || it.weapon==weapon) && (context=="All" || it.context==context) } && (opponent.isBlank() || s.name(if(b.a==id) b.b else b.a).contains(opponent,true)) }
    ScrollPage {
        Heading(p.name); Hint(listOf(p.club,p.handedness).filter { it.isNotBlank() }.joinToString(" · ")); if(p.notes.isNotBlank()) Text(p.notes)
        TextButton(onClick={edit=true}) {Text("Edit profile & aliases")}
        Panel("Latest score ratings") {
            listOf("Foil","Épée","Sabre").forEach {Text(ratingLabel(it,strength?.ratings?.get(it to id),strength==null))}
            Hint("Estimates from all recorded results, separate for each weapon. Provisional until 5 eligible bouts. These ratings use the full history, independently of the filters below.")
        }
        Chips(listOf("All","Foil","Épée","Sabre"),weapon,{weapon=it}); Chips(listOf("All","Training","Competition"),context,{context=it}); Chips(listOf("All","5","10","15"),target,{target=it})
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Input("From date",from,{from=it},modifier=Modifier.weight(1f)); Input("To date",to,{to=it},modifier=Modifier.weight(1f)) }
        Input("Opponent filter",opponent,{opponent=it})
        if(!p.isMe) StatsPanel(bouts.filter { it.contains(s.me) },s.me,"Your head-to-head with ${p.name}")
        StatsPanel(bouts,id,"${p.name} · all recorded opponents")
        Heading("Monthly performance")
        bouts.filter { it.stamp.day.length>=7 && !it.origin.uncertain }.groupBy { it.stamp.day.take(7) }.toSortedMap(reverseOrder()).entries.take(12).forEach { (month,rows)->val stats=statistics(rows,id); Hint("$month · ${stats.bouts} bouts · ${stats.wins}W / ${stats.losses}L · ${decimal(stats.touchShare?.times(100))}% touch share") }
        Heading("Match history")
        bouts.sortedByDescending { it.stamp.day }.take(100).forEach { BoutRow(s,it) {onBout(it)} }
    }
    if(edit) Form("Fencer profile",{edit=false},{run { repo.saveFencer(p.copy(name=name.trim(),aliases=aliases.split(',').map {it.trim()}.filter {it.isNotEmpty()},club=club,handedness=hand,notes=notes));edit=false }}) {
        Input("Name",name,{name=it}); Input("Aliases · comma separated",aliases,{aliases=it}); Input("Club",club,{club=it}); Chips(listOf("Right","Left","Unknown"),hand,{hand=it}); Input("Notes",notes,{notes=it},lines=3)
        Heading("Merge duplicate profile"); Hint("Choose the profile to keep. This moves bouts and aliases; it cannot merge two fencers who have fenced each other.")
        Input("Search profile to keep",mergeSearch,{mergeSearch=it})
        if(mergeSearch.isNotBlank()) s.fencers.filter { it.id!=id && it.name.contains(mergeSearch,true) }.take(6).forEach { candidate-> TextButton(onClick={merge=candidate}) {Text(candidate.name)} }
    }
    merge?.let { candidate -> AlertDialog(onDismissRequest={merge=null},title={Text("Merge into ${candidate.name}?")},text={Text("All of ${p.name}'s bouts will refer to ${candidate.name}. Names and aliases will be retained.")},confirmButton={TextButton(onClick={run {repo.mergeFencers(p.id,candidate.id);merge=null;edit=false}}) {Text("Merge profiles")}},dismissButton={TextButton(onClick={merge=null}) {Text("Cancel")}}) }
}
