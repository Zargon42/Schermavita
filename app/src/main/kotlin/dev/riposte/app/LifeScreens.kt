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
import dev.riposte.life.*
import java.time.*

@Composable fun TodayScreen(s: Snapshot,day: String,onDaySelect: (String)->Unit,onMetric: (Metric)->Unit,onReview: ()->Unit,onBout: ()->Unit,onEvent: (Observation)->Unit,onDay: (TrackingDay)->Unit) {
    val events=s.observations.filter { !it.deleted && it.stamp.day==day }
    var details by remember { mutableStateOf(false) }; val dayRecord=s.days.find { it.label==day && it.zone==ZoneId.systemDefault().id }
    val displayDate=runCatching { LocalDate.parse(day).format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM")) }.getOrDefault(day)
    ScrollPage {
        Heading(displayDate)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick={runCatching {onDaySelect(LocalDate.parse(day).minusDays(1).toString())}}) {Text("← Previous")}
            TextButton(onClick={onDaySelect(DayClock.day(Instant.now(),ZoneId.systemDefault(),s.settings.boundaryRules,s.days).label)}) {Text("Today")}
            TextButton(onClick={runCatching {onDaySelect(LocalDate.parse(day).plusDays(1).toString())}}) {Text("Next →")}
        }
        Panel("Your day, at a glance") {
            val steps=events.filter {it.metric=="steps"}.let {rows->rows.filter {it.scope=="daily"}.ifEmpty {rows}.maxByOrNull {it.stamp.createdAt}} ?: events.filter {it.metric=="legacy:fit_steps"}.lastOrNull(); val sleeps=events.filter {it.metric=="sleep"}.ifEmpty {events.filter {it.metric=="legacy:fit_sleep_hours"}}
            val sleepTotal=sleeps.filter {it.scope=="daily"}.maxByOrNull {it.stamp.createdAt}?.value ?: sleeps.takeIf {it.isNotEmpty()}?.sumOf {it.value ?: 0.0}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Stat("Steps",decimal(steps?.value)); Stat("Sleep hours",decimal(sleepTotal)); Stat("Your bouts",s.bouts.count { !it.deleted && it.contains(s.me) && it.stamp.day==day }.toString()) }
            Hint(if(dayRecord!=null) "Tracking day ends ${Instant.parse(dayRecord.end).atZone(ZoneId.of(dayRecord.zone)).format(java.time.format.DateTimeFormatter.ofPattern("EEE HH:mm"))}" else "Historical day · source date basis is retained")
            Hint(s.settings.healthStatus)
            val screen=events.filter {it.metric=="screen_time"}.let {rows->rows.filter {it.scope=="daily"}.ifEmpty {rows}.maxByOrNull {it.stamp.createdAt}}
            if(s.settings.screenTimeEnabled || screen!=null) {
                Text("Screen time: ${screen?.value?.let { "${decimal(it)} min" } ?: "Unknown"}${if(screen?.origin?.uncertain==true) " · partial" else ""}")
                Hint("Interactive, unlocked time · synced through ${screen?.let {localTime(it.stamp)} ?: "no reading yet"}")
            }
            if(steps?.origin?.dateBasis=="calendar" || sleeps.any {it.origin.dateBasis=="calendar"}) Hint("Historical health totals use calendar dates.")
        }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { Button(onClick=onBout,Modifier.weight(1f)) {Text("Add bout")}; OutlinedButton(onClick=onReview,Modifier.weight(1f)) {Text(if(s.reviews.any {it.day==day}) "Review saved ✓" else "Review day")} }
        Heading("Quick entry")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            s.metrics.filter { !it.hidden && !it.symptom }.sortedBy {it.order}.forEach { m-> AssistChip(onClick={onMetric(m)},label={Text(m.name)}) }
        }
        Heading("Symptoms")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { s.metrics.filter { !it.hidden && it.symptom }.sortedBy {it.order}.forEach {m->AssistChip(onClick={onMetric(m)},label={Text(m.name)})} }
        TextButton(onClick={details=true}) {Text("Day notes")}
        Heading("Day log")
        if(events.isEmpty()) Hint("Nothing recorded yet. Missing entries stay unknown.")
        events.sortedByDescending {it.stamp.occurredAt}.take(80).forEach { e-> EventRow(s,e) {onEvent(e)} }
    }
    if(details) {
        val record=remember { dayRecord ?: DayClock.day(LocalDate.parse(day).atTime(12,0).atZone(ZoneId.systemDefault()).toInstant(),ZoneId.systemDefault(),s.settings.boundaryRules,s.days) }
        var notes by remember {mutableStateOf(record.notes)}
        Form("Day details",{details=false},{onDay(record.copy(notes=notes));details=false}) { Input("Notes",notes,{notes=it},lines=4); record.willingness?.let { Hint("Historical daily willingness to fence: $it / 5") }; Hint("Record willingness for each session in Session details.") }
    }
}
@Composable fun EventRow(s: Snapshot,e: Observation,onClick: ()->Unit) {
    val m=s.metrics.find {it.id==e.metric}?.atScale(e.scale)
    Card(Modifier.fillMaxWidth().clickable(onClick=onClick)) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(m?.name ?: e.metric,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f)); if(e.value!=null) Text("${if(m?.kind=="Rating") ratingNumber(e.value!!) else decimal(e.value)} ${e.unit}") }
        if(e.value!=null && m?.kind=="Rating") Hint(m.definition().levels.find {it.value==e.value}?.label.orEmpty())
        if(e.text.isNotBlank()) Text(e.text.take(240),maxLines=3)
        Hint("${if(e.origin.precision=="instant") localTime(e.stamp) else e.stamp.day.ifBlank {"Undated"}} · ${e.scope} · ${e.origin.source}")
        if(e.origin.dateBasis=="calendar") Hint("Original calendar-day record")
        if(e.origin.uncertain) Hint("Needs review · excluded from comparisons")
    } }
}
@Composable fun ValueInput(m: Metric,value: String,onValue: (String)->Unit) {
    when(m.kind) {
        "Rating" -> DefinitionInput(m.definition(),value,onValue)
        "Yes/no" -> Chips(listOf("Yes","No"),when(value) {"1","1.0"->"Yes";"0","0.0"->"No";else->""},{onValue(if(it=="Yes") "1" else "0")})
        "Tag" -> { if(m.choices.isNotEmpty()) Chips(m.choices,value,onValue); Input("Tag",value,onValue) }
        "Text" -> Input("Entry",value,onValue,lines=4)
        else -> Input("${m.name} ${m.unit}",value,onValue,true)
    }
}
@Composable fun EventForm(s: Snapshot,metric: Metric,existing: Observation?,selectedDay: String,onClose: ()->Unit,onSave: (Observation)->Unit,onDelete: (Observation)->Unit) {
    val m=existing?.let {metric.atScale(it.scale)} ?: metric
    val healthOverride=existing?.origin?.source in listOf("Health Connect","Android Usage Access")
    val id=remember {if(healthOverride) "manual-override:${existing!!.metric}:${existing.stamp.day}" else existing?.id ?: newId()}; val initial=remember {existing?.stamp ?: DayClock.stamp(Instant.now(),s.settings,s.days,selectedDay)}
    var value by remember {mutableStateOf(existing?.value?.toString() ?: existing?.text.orEmpty())}; var note by remember {mutableStateOf(if(existing?.value!=null) existing.text else "")}
    var time by remember {mutableStateOf(localTime(initial))}; var day by remember {mutableStateOf(initial.day)}; var scope by remember {mutableStateOf(if(healthOverride || existing==null && m.id in listOf("sleep","steps")) "daily" else existing?.scope ?: "momentary")}
    var advanced by remember {mutableStateOf(existing!=null)}; var error by remember {mutableStateOf("")}
    Form(m.name,onClose,{try {
        LocalDate.parse(day); val numeric=if(m.kind in listOf("Text","Tag")) null else value.toDoubleOrNull() ?: error("Enter a value.")
        if(numeric!=null) require(numeric.isFinite() && (existing?.origin?.let {it.source!="manual"}==true || numeric in m.min..m.max)) {"Value must be between ${m.min} and ${m.max}."}
        require(value.isNotBlank()) {"Enter a value."}
        if(numeric!=null && m.kind=="Rating") require(m.definition().levels.any {it.value==numeric}) {"Choose a rating level."}
        val actual=if(time==localTime(initial)) initial.occurredAt else if(time.isBlank() && existing?.origin?.precision!="instant") "" else parsedTime(time,initial.zone).toString()
        val provenance=if(healthOverride) Origin("manual",externalId=existing!!.id,raw="Manual daily total overriding a ${existing.origin.source} reading") else existing?.origin ?: Origin()
        onSave(Observation(id,m.id,initial.copy(occurredAt=actual,createdAt=if(healthOverride) Instant.now().toString() else initial.createdAt,day=day,dayOverride=true),numeric,if(numeric==null) value else note,scope,existing?.unit ?: m.unit,existing?.scale ?: m.scale,provenance,endAt=existing?.endAt))
    } catch(e: Exception) {error=e.message ?: "Check the entry."}}) {
        if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
        if(healthOverride) Hint("Saving creates a manual total for this tracking day. The original synced readings are retained separately.")
        ValueInput(m,value,{value=it}); if(m.kind !in listOf("Text","Tag")) Input("Optional note",note,{note=it},lines=2)
        Hint("For tracking day $day")
        TextButton(onClick={advanced=!advanced}) {Text("Time, day & entry type")}
        if(advanced) {Input("Occurred · yyyy-MM-dd HH:mm",time,{time=it});Input("Tracking day · yyyy-MM-dd",day,{day=it});Chips(listOf("momentary","daily"),scope,{scope=it});Hint("Actual timestamps are retained even when the entry belongs to the previous day.");existing?.let {Hint("Original source: ${it.origin.source} · ${it.origin.dateBasis} · ${it.scale}"); if(it.origin.raw.isNotBlank()) Text(it.origin.raw.take(5000),style=MaterialTheme.typography.bodySmall)} }
        if(existing!=null) TextButton(onClick={onDelete(existing)}) {Text("Delete entry",color=MaterialTheme.colorScheme.error)}
    }
}
@Composable fun ReviewForm(s: Snapshot,day: String,onClose: ()->Unit,onSave: (List<Observation>,DailyReview)->Unit) {
    val metrics=remember {s.metrics.filter { !it.hidden && (it.kind in listOf("Rating","Yes/no") || it.id in listOf("exercise","caffeine")) }.sortedBy {it.order}.map {m->s.observations.find {it.id=="review:$day:${m.id}" && !it.deleted}?.let {m.atScale(it.scale)} ?: m}}
    val values=remember {mutableStateMapOf<String,String>().apply { metrics.forEach {m->
        val rows=s.observations.filter {!it.deleted && it.metric==m.id && it.stamp.day==day && it.scale==m.scale}
        val previous=rows.lastOrNull {it.scope=="daily"}?.value ?: if(m.symptom || m.kind=="Yes/no") rows.mapNotNull {it.value}.maxOrNull() else null
        if(previous!=null) put(m.id,previous.toString())
    } } }
    var notes by remember {mutableStateOf(s.reviews.find {it.day==day}?.notes.orEmpty())};var error by remember {mutableStateOf("")}
    Form("Review $day",onClose,{try {
        val now=Instant.now(); val rows=metrics.mapNotNull {m->values[m.id]?.takeIf {it.isNotBlank()}?.let {v->val n=v.toDouble();require(n in m.min..m.max);if(m.kind=="Rating") require(m.definition().levels.any {it.value==n});Observation("review:$day:${m.id}",m.id,DayClock.stamp(now,s.settings,s.days,day),n,scope="daily",unit=m.unit,scale=m.scale)} }
        onSave(rows,DailyReview(day,day,now.toString(),notes))
    } catch(e: Exception) {error="Check the entered values."}}) {
        Hint("Only answered items are recorded. Overall daily ratings stay separate from momentary entries.")
        if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
        metrics.filterNot {it.symptom}.forEach {m-> Text(m.name,fontWeight=FontWeight.SemiBold);ValueInput(m,values[m.id].orEmpty(),{values[m.id]=it});TextButton(onClick={values.remove(m.id)}) {Text("Unknown / skip")} }
        Heading("Symptoms")
        TextButton(onClick={metrics.filter {it.symptom && values[it.id].isNullOrBlank() && it.definition().levels.any {level->level.value==0.0 && level.label.equals("None",true)}}.forEach {values[it.id]="0"}}) {Text("Remaining symptoms: none today")}
        metrics.filter {it.symptom}.forEach {m->Text(m.name);ValueInput(m,values[m.id].orEmpty(),{values[m.id]=it});TextButton(onClick={values.remove(m.id)}) {Text("Unknown / skip")} }
        Input("Day notes",notes,{notes=it},lines=3)
    }
}
@Composable fun HistoryScreen(s: Snapshot,onEvent: (Observation)->Unit,onBout: (Bout)->Unit) {
    var query by remember {mutableStateOf("")};var kind by remember {mutableStateOf("All")};var limit by remember {mutableStateOf(60)}
    ScrollPage {
        Heading("Your record");Input("Search names, notes, metrics, or date",query,{query=it;limit=60});Chips(listOf("All","Life","Fencing","Needs review"),kind,{kind=it;limit=60})
        val events=if(kind=="Fencing") emptyList() else s.observations.filter { !it.deleted && (kind!="Needs review" || it.origin.uncertain || it.stamp.day.isBlank()) && (it.stamp.day.contains(query) || it.text.contains(query,true) || s.metrics.find {m->m.id==it.metric}?.name?.contains(query,true)==true) }
        val bouts=if(kind=="Life") emptyList() else s.bouts.filter { !it.deleted && (kind!="Needs review" || it.origin.uncertain) && (it.stamp.day.contains(query) || s.name(it.a).contains(query,true) || s.name(it.b).contains(query,true) || it.notes.contains(query,true)) }
        val entries=(events.map {Triple(it.stamp.day,it.id,it as Any)}+bouts.map {Triple(it.stamp.day,it.id,it as Any)}).sortedByDescending {it.first}
        Hint("${entries.size} matching records")
        entries.take(limit).forEach { (_,_,entry)->when(entry) {is Observation->EventRow(s,entry) {onEvent(entry)};is Bout->BoutRow(s,entry) {onBout(entry)}} }
        if(entries.size>limit) TextButton(onClick={limit+=100}) {Text("Show 100 more")}
    }
}
@Composable fun MetricsScreen(repo: AppRepository,s: Snapshot,run: (suspend ()->Unit)->Unit) {
    var editMetric by remember {mutableStateOf<Metric?>(null)};var editSession by remember {mutableStateOf<Int?>(null)}
    var add by remember {mutableStateOf(false)}; var name by remember {mutableStateOf("")};var kind by remember {mutableStateOf("Rating")};var unit by remember {mutableStateOf("")};var min by remember {mutableStateOf("0")};var max by remember {mutableStateOf("5")};var symptom by remember {mutableStateOf(false)};var choices by remember {mutableStateOf("")}
    ScrollPage {
        Heading("Make it yours");Button(onClick={add=true}) {Text("Add tracker")};Hint("Hide or move trackers without deleting their records. New scales get new identifiers, so old values keep their meaning.")
        Heading("Daily trackers")
        s.metrics.filterNot {it.id.startsWith("legacy:")}.sortedBy {it.order}.forEach {m->
            ListItem(headlineContent={Text(m.name)},supportingContent={Text(if(m.kind=="Rating") "${m.definition().levels.size} rating levels" else "${m.kind} ${m.unit}")},leadingContent={Switch(!m.hidden,{shown->run {repo.change {it.copy(metrics=it.metrics.replace(m.copy(hidden=!shown)) {id})}}})},trailingContent={Column {TextButton(onClick={editMetric=m}) {Text("Edit")};TextButton(onClick={run {repo.change {current->val ordered=current.metrics.sortedBy {it.order}.toMutableList();val i=ordered.indexOfFirst {it.id==m.id};if(i>0) java.util.Collections.swap(ordered,i,i-1);current.copy(metrics=ordered.mapIndexed {n,v->v.copy(order=n)})}}}) {Text("↑")};TextButton(onClick={run {repo.change {current->val ordered=current.metrics.sortedBy {it.order}.toMutableList();val i=ordered.indexOfFirst {it.id==m.id};if(i>=0 && i<ordered.lastIndex) java.util.Collections.swap(ordered,i,i+1);current.copy(metrics=ordered.mapIndexed {n,v->v.copy(order=n)})}}}) {Text("↓")}}})
        }
        Heading("Session trackers")
        s.settings.sessionTrackers.forEachIndexed {index,d->ListItem(headlineContent={Text(d.name)},supportingContent={Text(d.kind)},trailingContent={TextButton(onClick={editSession=index}) {Text("Edit")}})}
    }
    editMetric?.let { m -> DefinitionForm(m.definition(),{editMetric=null},{d->run {repo.change {current->current.copy(metrics=current.metrics.replace(m.edited(d)) {id})};editMetric=null}}) }
    editSession?.let { index -> val initial=s.settings.sessionTrackers[index];DefinitionForm(initial,{editSession=null},{d->run {repo.change {current->current.copy(settings=current.settings.copy(sessionTrackers=current.settings.sessionTrackers.mapIndexed {i,v->if(i==index) d.copy(id=if(d==initial) initial.id else newId()) else v}))};editSession=null}}) }
    if(add) Form("New tracker",{add=false},{run {
        require(name.isNotBlank());val low=if(kind=="Yes/no") 0.0 else min.toDouble();val high=if(kind=="Yes/no") 1.0 else max.toDouble();require(low.isFinite() && high.isFinite() && low<=high)
        if(kind=="Rating") require(low%1.0==0.0 && high%1.0==0.0 && high-low<=999) {"Use whole-number rating bounds with at most 1,000 levels. Edit the tracker afterward for custom values and labels."}
        val m=Metric(name=name.trim(),kind=kind,unit=unit,min=low,max=high,scale="custom:${newId()}",order=s.metrics.size,symptom=symptom,choices=choices.split(',').map {it.trim()}.filter {it.isNotBlank()})
        repo.change {it.copy(metrics=it.metrics+m)};add=false;name=""
    }}) {Input("Name",name,{name=it});Chips(listOf("Rating","Number","Duration","Yes/no","Counter","Tag","Text"),kind,{kind=it});Input("Unit",unit,{unit=it});Input("Minimum",min,{min=it},true);Input("Maximum",max,{max=it},true);Row {Checkbox(symptom,{symptom=it;if(it){kind="Rating";min="0";max="3"}});Text("Include in symptom checklist")};Input("Tag choices · comma separated",choices,{choices=it})}
}
