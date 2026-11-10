package dev.riposte.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import dev.riposte.core.*
import dev.riposte.fencing.*
import dev.riposte.insights.*
import java.time.LocalDate

@Composable fun EquipmentPicker(s: Snapshot,owner: String,kind: String,id: String,onPick: (EquipmentProfile)->Unit) {
    var open by remember {mutableStateOf(false)}
    Box {TextButton(onClick={open=true}) {Text("Choose $kind")}
        DropdownMenu(open,{open=false}) {s.equipmentProfiles.filter {it.owner==owner && it.kind==kind && (!it.retired || it.id==id)}.forEach {p->
            DropdownMenuItem(text={Text(p.name)},onClick={onPick(p);open=false})
        }}}
}

@Composable fun EquipmentScreen(repo: AppRepository,s: Snapshot,run: (suspend ()->Unit)->Unit,onClose: ()->Unit) {
    var search by remember {mutableStateOf("")};var editing by remember {mutableStateOf<EquipmentProfile?>(null)}
    var selected by remember {mutableStateOf<String?>(null)};var merge by remember {mutableStateOf<EquipmentProfile?>(null)}
    var from by remember {mutableStateOf("")};var to by remember {mutableStateOf("")}
    val current=s.equipmentProfiles.find {it.id==selected}
    if(editing!=null) {
        var p by remember(editing!!.id) {mutableStateOf(editing!!)};var error by remember {mutableStateOf("")}
        Form("Equipment profile",{editing=null},{try {require(p.name.isNotBlank());if(p.firstUsed.isNotBlank()) LocalDate.parse(p.firstUsed);run {repo.change {it.copy(equipmentProfiles=it.equipmentProfiles.filterNot {it.id==p.id}+p)};editing=null}} catch(e: Exception){error="Enter a name and valid first-used date."}}) {
            if(error.isNotBlank()) Text(error)
            Input("Name",p.name,{p=p.copy(name=it)})
            Input("Aliases, separated by commas",p.aliases.joinToString(","),{p=p.copy(aliases=it.split(',').map {v->v.trim()}.filter {v->v.isNotBlank()})})
            if(p.kind=="Foil") {Input("Blade type",p.blade,{p=p.copy(blade=it)});Input("Handle type",p.handle,{p=p.copy(handle=it)});Input("Set-angle intensity",p.angle,{p=p.copy(angle=it)});Chips(listOf("None","Light","Medium","Strong"),p.angle,{p=p.copy(angle=it)})}
            else {Input("Brand / model",p.brand,{p=p.copy(brand=it)});Input("Size",p.size,{p=p.copy(size=it)})}
            Input("First used · yyyy-MM-dd",p.firstUsed,{p=p.copy(firstUsed=it)})
            Input("Notes",p.notes,{p=p.copy(notes=it)},lines=3)
            Row {Checkbox(p.retired,{p=p.copy(retired=it)});Text(if(p.kind=="Foil") "Broken" else "Retired")}
            var extra by remember {mutableStateOf(p.properties.entries.joinToString("\n") {"${it.key}=${it.value}"})}
            Input("Extra properties · one name=value per line",extra,{extra=it;p=p.copy(properties=it.lines().mapNotNull {line->line.split('=',limit=2).takeIf {v->v.size==2 && v[0].isNotBlank()}?.let {v->v[0].trim() to v[1].trim()}}.toMap())},lines=3)
        };return
    }
    Form("Equipment",onClose) {
        Input("Find equipment",search,{search=it})
        Row {Button(onClick={editing=EquipmentProfile(owner=s.me,name="")}) {Text("Add foil")};TextButton(onClick={editing=EquipmentProfile(owner=s.me,kind="Glove",name="")}) {Text("Add glove")}}
        if(current==null) s.equipmentProfiles.filter {it.owner==s.me && (it.name.contains(search,true) || it.aliases.any {a->a.contains(search,true)})}.forEach {p->
            OutlinedButton(onClick={selected=p.id}) {Text("${p.name} · ${p.kind}${if(p.retired) " · Inactive" else ""}")}
        } else {
            TextButton(onClick={selected=null}) {Text("All equipment")}
            Heading(current.name);Hint(listOf(current.blade,current.handle,current.angle,current.brand,current.size).filter {it.isNotBlank()}.joinToString(" · "))
            Text(current.notes);Hint("First used: ${current.firstUsed.ifBlank {"Unknown"}}")
            TextButton(onClick={editing=current}) {Text("Edit")}
            TextButton(onClick={editing=current.copy(id=newId(),name=current.name+" (new configuration)",aliases=emptyList(),firstUsed="",retired=false)}) {Text("Create from this ${current.kind.lowercase()}")}
            Input("From date",from,{from=it});Input("To date",to,{to=it})
            val bouts=s.bouts.filter {b->val d=if(b.a==s.me) b.detailsA else b.detailsB;!b.deleted && b.contains(s.me) && (d.swordId==current.id || d.gloveId==current.id) && (from.isBlank() || b.stamp.day>=from) && (to.isBlank() || b.stamp.day<=to)}
            StatsPanel(bouts,s.me);Hint("${bouts.map {it.sessionId}.distinct().size} sessions")
            val points=performancePoints(bouts,s.me,PerformanceMeasure.TOUCH_SHARE)
            if(points.isNotEmpty()) ObservationChart(points.map {ChartDot(LocalDate.parse(it.day).toEpochDay().toDouble(),it.value,it.basis,it.day)},"Recorded date","Touch share %",dateAxis=true)
            bouts.forEach {BoutRow(s,it) {}}
            Heading("Merge duplicate into this profile")
            Hint("The surviving profile's properties win. Edit them above first to resolve conflicts. Original recorded names remain in history.")
            s.equipmentProfiles.filter {it.owner==current.owner && it.kind==current.kind && it.id!=current.id}.forEach {candidate->TextButton(onClick={merge=candidate}) {Text(candidate.name)}}
            merge?.let {source->
                Text("Merge ${source.name} into ${current.name}. Source properties: ${listOf(source.blade,source.handle,source.angle,source.brand,source.size,source.properties.toString()).joinToString(" · ")}")
                Text("Source first used: ${source.firstUsed.ifBlank {"Unknown"}} · inactive: ${source.retired}")
                Text("Source notes: ${source.notes.ifBlank {"None"}}")
                Text("Keep first used: ${current.firstUsed.ifBlank {"Unknown"}} · inactive: ${current.retired}")
                Text("Keep notes: ${current.notes.ifBlank {"None"}}")
                Button(onClick={run {repo.change {it.mergeEquipment(source.id,current.id)};merge=null}}) {Text("Confirm merge")}
                TextButton(onClick={merge=null}) {Text("Cancel merge")}
            }
        }
    }
}
