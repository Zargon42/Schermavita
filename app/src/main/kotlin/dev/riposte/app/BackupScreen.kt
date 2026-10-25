package dev.riposte.app
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import dev.riposte.core.*
import dev.riposte.imports.Csv
import dev.riposte.life.*
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

fun boutsCsv(s: Snapshot): String = Csv.write(listOf(listOf("bout_id","session_id","day","occurred_at","zone","precision","date_basis","fencer_a_id","fencer_a","fencer_b_id","fencer_b","score_a","score_b","winner_id","status","target","weapon","context","round","order","source","uncertain","sword_a","glove_a","lenses_a","equipment_a","sword_b","glove_b","lenses_b","equipment_b"))+s.bouts.filterNot {it.deleted}.map {b->val session=s.sessions.find {it.id==b.sessionId};listOf(b.id,b.sessionId,b.stamp.day,b.stamp.occurredAt,b.stamp.zone,b.origin.precision,b.origin.dateBasis,b.a,s.name(b.a),b.b,s.name(b.b),b.scoreA?.toString().orEmpty(),b.scoreB?.toString().orEmpty(),b.winner().orEmpty(),b.status,b.target?.toString().orEmpty(),session?.weapon.orEmpty(),session?.context.orEmpty(),b.round,b.order?.toString().orEmpty(),b.origin.source,b.origin.uncertain.toString(),b.detailsA.sword,b.detailsA.glove,b.detailsA.lenses?.toString().orEmpty(),equipmentJson(s,b.detailsA.equipment),b.detailsB.sword,b.detailsB.glove,b.detailsB.lenses?.toString().orEmpty(),equipmentJson(s,b.detailsB.equipment))})
fun equipmentJson(s: Snapshot,values: Map<String,String>) = codec.encodeToString(values.map { (id,value)->mapOf("id" to id,"category" to (s.settings.equipmentCategories.find {it.id==id}?.name ?: id),"value" to value) })
fun sessionsCsv(s: Snapshot): String = Csv.write(listOf(listOf("session_id","name","day","occurred_at","willingness","willingness_definition","warmup","warmup_definition","transport","transport_definition","sword","glove","lenses","equipment"))+s.sessions.map { session ->
    listOf(session.id,session.name,session.stamp.day,session.stamp.occurredAt)+listOf("willingness","warmup","transport").flatMap {key->val answer=session.preparation[key];listOf(answer?.value.orEmpty(),answer?.let {codec.encodeToString(it.definition)}.orEmpty())}+listOf(session.sword,session.glove,session.lenses?.toString().orEmpty(),equipmentJson(s,session.equipment))
})
fun lessonsCsv(s: Snapshot): String = Csv.write(listOf(listOf("lesson_id","session_id","session_name","day","coach","focus","notes"))+s.lessons.map {lesson->
    val session=s.sessions.find {it.id==lesson.sessionId}
    listOf(lesson.id,lesson.sessionId,session?.name.orEmpty(),session?.stamp?.day.orEmpty(),lesson.coach,lesson.focus,lesson.notes)
})
@Composable fun BackupScreen(repo: AppRepository,s: Snapshot,run: (suspend ()->Unit)->Unit) {
    val context=LocalContext.current;var password by remember {mutableStateOf("")};var status by remember {mutableStateOf("")};var pending by remember {mutableStateOf<BackupArchive.Contents?>(null)};var exportKind by remember {mutableStateOf("bouts")}
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {uri->if(uri!=null) run {withContext(Dispatchers.IO) {val bytes=BackupCipher.encrypt(BackupArchive.pack(repo.state.value,repo::sourceFile),password.toCharArray());context.contentResolver.openOutputStream(uri,"wt")!!.use {it.write(bytes)}};repo.change {it.copy(settings=it.settings.copy(lastBackup=Instant.now().toString()))};status="Encrypted backup saved. Keep its password separately.";password=""}}
    val restore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->if(uri!=null) run {pending=withContext(Dispatchers.IO) {val bytes=context.contentResolver.openInputStream(uri)!!.use {it.readBounded(250_000_001)};require(bytes.size<=250_000_000);BackupArchive.unpack(BackupCipher.decrypt(bytes,password.toCharArray()))};status="Backup validated. Confirm replacement below."}}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {uri->if(uri!=null) run {withContext(Dispatchers.IO) {
        val text=when(exportKind) {"bouts"->boutsCsv(s);"sessions"->sessionsCsv(s);"lessons"->lessonsCsv(s);"fencers"->Csv.write(listOf(listOf("id","name","is_me","aliases","club","handedness","notes"))+s.fencers.map {listOf(it.id,it.name,it.isMe.toString(),it.aliases.joinToString("|"),it.club,it.handedness,it.notes)})
            else->Csv.write(listOf(listOf("id","metric_id","metric","value","unit","scale","text","scope","day","occurred_at","created_at","zone","source","date_basis","uncertain","scale_definition"))+s.observations.filterNot {it.deleted}.map {listOf(it.id,it.metric,s.metrics.find {m->m.id==it.metric}?.name.orEmpty(),it.value?.toString().orEmpty(),it.unit,it.scale,it.text,it.scope,it.stamp.day,it.stamp.occurredAt,it.stamp.createdAt,it.stamp.zone,it.origin.source,it.origin.dateBasis,it.origin.uncertain.toString(),s.metrics.find {m->m.id==it.metric}?.atScale(it.scale)?.definition()?.let {d->codec.encodeToString(d)}.orEmpty())})}
        context.contentResolver.openOutputStream(uri,"wt")!!.use {it.write(text.toByteArray())}
    };status="CSV exported."}}
    ScrollPage {
        Heading("Keep your record safe");Hint("Backups include records, settings, and imported source attachments. CSV exports are unencrypted and intended for your own analysis.")
        Hint("Last backup: ${s.settings.lastBackup.ifBlank {"No backup yet"}}")
        OutlinedTextField(password,{password=it},label={Text("Backup password · at least 8 characters")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
        Button(onClick={save.launch("schermavita-${java.time.LocalDate.now()}.riposte")},enabled=password.length>=8,modifier=Modifier.fillMaxWidth()) {Text("Save encrypted backup")}
        OutlinedButton(onClick={restore.launch(arrayOf("*/*"))},enabled=password.isNotBlank(),modifier=Modifier.fillMaxWidth()) {Text("Open backup to restore")}
        Heading("Export for analysis")
        listOf("sessions","lessons","bouts","fencers","life").forEach {kind->OutlinedButton(onClick={exportKind=kind;export.launch("schermavita-$kind.csv")},Modifier.fillMaxWidth()) {Text("Export $kind CSV")}}
        if(status.isNotBlank()) Text(status)
    }
    pending?.let {contents->AlertDialog(onDismissRequest={pending=null},title={Text("Replace this phone's records?")},text={Text("The backup contains ${contents.snapshot.bouts.size} bouts and ${contents.snapshot.observations.size} life records. Restoring replaces current records. Export a backup first if you want to keep them.")},confirmButton={TextButton(onClick={run {
        withContext(Dispatchers.IO) {contents.sources.forEach {(id,bytes)->val file=repo.sourceFile(id);file.parentFile!!.mkdirs();if(file.exists()) require(file.readBytes().contentEquals(bytes)) {"Attachment identifier conflict."} else file.writeBytes(bytes)}}
        repo.change {contents.snapshot};ReminderWorker.schedule(context,contents.snapshot.settings);pending=null;password="";status="Backup restored."
    }}) {Text("Restore backup")}},dismissButton={TextButton(onClick={pending=null}) {Text("Cancel")}}) }
}
