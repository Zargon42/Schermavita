@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package dev.riposte.app
import android.graphics.Bitmap
import android.graphics.Matrix
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.riposte.core.*
import dev.riposte.fencing.*
import dev.riposte.imports.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.security.MessageDigest
import java.time.*
import kotlinx.serialization.*

fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
data class ReviewedResult(val key: String=newId(),val draft: ResultDraft,val include: Boolean=true,val reviewed: Boolean=false,val linkTo: String="",val aId: String?=null,val bId: String?=null)
fun mergeHistory(current: Snapshot,imported: Snapshot): Snapshot {
    imported.validate()
    val people=current.fencers.toMutableList();val mapping=mutableMapOf<String,String>()
    imported.fencers.forEach {p->
        val existing=if(p.isMe) people.first {it.isMe} else people.find {it.id==p.id} ?: if(p.identityKnown) people.filter {!it.isMe && it.identityKnown && normalizedName(it.name)==normalizedName(p.name)}.singleOrNull() else null
        mapping[p.id]=existing?.id ?: p.id;if(existing==null) people.add(p)
    }
    return current.copy(fencers=people,sessions=(current.sessions+imported.sessions).distinctBy {it.id},
        bouts=(current.bouts+imported.bouts.map {it.copy(a=mapping.getValue(it.a),b=mapping.getValue(it.b))}).distinctBy {it.id},
        metrics=(current.metrics+imported.metrics).distinctBy {it.id},observations=(current.observations+imported.observations).distinctBy {it.id},
        batches=(current.batches+imported.batches).distinctBy {it.id},attachments=(current.attachments+imported.attachments).distinctBy {it.id})
}
@Composable fun ImportScreen(repo: AppRepository,s: Snapshot,run: (suspend ()->Unit)->Unit) {
    val context=LocalContext.current
    var uri by remember {mutableStateOf<Uri?>(null)};var filename by remember {mutableStateOf("")};var bytes by remember {mutableStateOf<ByteArray?>(null)}
    var sourceMime by remember {mutableStateOf("application/octet-stream")}
    var tableText by remember {mutableStateOf("")};var sheets by remember {mutableStateOf<Map<String,List<List<String>>>>(emptyMap())}
    var bitmap by remember {mutableStateOf<Bitmap?>(null)};var pdf by remember {mutableStateOf(false)};var page by remember {mutableStateOf("1")};var pages by remember {mutableStateOf(1)}
    var left by remember {mutableStateOf("0")};var top by remember {mutableStateOf("0")};var right by remember {mutableStateOf("100")};var bottom by remember {mutableStateOf("100")}
    var rows by remember {mutableStateOf("7")};var columns by remember {mutableStateOf("7")};var columnCuts by remember {mutableStateOf("")};var rowCuts by remember {mutableStateOf("")}
    var recognized by remember {mutableStateOf<OcrResult?>(null)};var history by remember {mutableStateOf<BackupArchive.Contents?>(null)}
    var layout by remember {mutableStateOf("Bout rows")};var header by remember {mutableStateOf(true)};var mapping by remember {mutableStateOf("1,2,3,4")}
    var eventName by remember {mutableStateOf("Tournament")};var day by remember {mutableStateOf(LocalDate.now().toString())};var weapon by remember {mutableStateOf("Foil")};var round by remember {mutableStateOf("")};var target by remember {mutableStateOf("5")}
    var results by remember {mutableStateOf<List<ReviewedResult>>(emptyList())};var status by remember {mutableStateOf("")};var busy by remember {mutableStateOf(false)};var allowRepeat by remember {mutableStateOf(false)};var undo by remember {mutableStateOf<ImportBatch?>(null)}
    var expandedBatch by remember {mutableStateOf("")};var reviewBout by remember {mutableStateOf<Bout?>(null)}
    val cameraFile=remember {File(context.cacheDir,"camera/poule.jpg").apply {parentFile!!.mkdirs()}}
    val cameraUri=remember {FileProvider.getUriForFile(context,"${context.packageName}.files",cameraFile)}
    fun open(selected: Uri,nameOverride: String?=null) {run {
        busy=true
        try {
            val loaded=readImportSource(context,selected,nameOverride)
            uri=selected;filename=loaded.name;bytes=loaded.raw;sourceMime=loaded.mime
            bitmap=loaded.bitmap;history=loaded.history;sheets=loaded.sheets;tableText=loaded.table
            pdf=loaded.pdf;pages=loaded.pages;page="1"
            results=emptyList();recognized=null;allowRepeat=false;status=""
            left="0";top="0";right="100";bottom="100"
        } finally {busy=false}
    } }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {if(it!=null) open(it)}
    val photoPicker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {if(it!=null) open(it)}
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) {if(it) open(cameraUri,"poule.jpg")}
    ScrollPage {
        Heading("Bring your records together")
        Hint("Import your prepared life history, CSV/XLSX results, or a photographed/PDF poule. Every tournament import is reviewed before saving.")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick={photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},enabled=!busy) {Text("Choose photo")}
            OutlinedButton(onClick={picker.launch(arrayOf("*/*"))},enabled=!busy) {Text("Choose file")}
            OutlinedButton(onClick={camera.launch(cameraUri)},enabled=!busy) {Text("Photograph sheet")}
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(filename.isNotBlank()) Text(filename)
        if(status.isNotBlank()) Text(status)
        val repeat=bytes?.let {raw->s.batches.any {it.hash==digest(raw)}} ?: false
        if(repeat) {Text("This file was imported before.");Row {Checkbox(allowRepeat,{allowRepeat=it});Text("Import another page / different results from this file")}}
        history?.let { contents ->
            val h=contents.snapshot
            Panel("Historical import preview") {
                Text("${h.fencers.size} fencer profiles · ${h.sessions.size} sessions · ${h.bouts.size} bouts · ${h.observations.size} life records")
                Hint("${h.bouts.count {it.origin.uncertain}} bouts have uncertain dates. Original scales and date bases are retained. Exact, unique name matches link to existing profiles; other names create profiles. Existing record IDs are kept unchanged.")
                Button(onClick={run {busy=true;try {
                    withContext(Dispatchers.IO) {contents.sources.forEach {(id,b)->val file=repo.sourceFile(id);file.parentFile!!.mkdirs();if(file.exists()) require(file.readBytes().contentEquals(b)) else file.writeBytes(b)}}
                    repo.change {mergeHistory(it,h)};status="History imported. Re-importing this package will not duplicate records.";history=null
                } finally {busy=false}}},enabled=!busy && !repeat) {Text("Import reviewed history")}
            }
        }
        bitmap?.let { image ->
            Image(image.asImageBitmap(),"Source page",Modifier.fillMaxWidth().heightIn(max=380.dp))
            if(pdf) {Input("PDF page · 1 to $pages",page,{page=it},true);TextButton(onClick={run {val n=page.toInt();require(n in 1..pages);bitmap=withContext(Dispatchers.IO) {DocumentReader.bitmap(context,uri!!,true,n-1)};recognized=null}}) {Text("Load page")}}
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {OutlinedButton(onClick={bitmap=Bitmap.createBitmap(image,0,0,image.width,image.height,Matrix().apply {postRotate(90f)},true);recognized=null}) {Text("Rotate 90°")};OutlinedButton(onClick={run {bitmap=withContext(Dispatchers.IO) {DocumentReader.bitmap(context,uri!!,pdf,(page.toIntOrNull() ?: 1)-1)};recognized=null}}) {Text("Reset image")}}
            Hint("Crop to the table, including names. Enter percentage edges measured from the top-left corner.")
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {Input("Left %",left,{left=it},true,modifier=Modifier.weight(1f));Input("Top %",top,{top=it},true,modifier=Modifier.weight(1f))}
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {Input("Right %",right,{right=it},true,modifier=Modifier.weight(1f));Input("Bottom %",bottom,{bottom=it},true,modifier=Modifier.weight(1f))}
            Button(onClick={run {val l=left.toFloat();val t=top.toFloat();val r=right.toFloat();val b=bottom.toFloat();require(l>=0 && t>=0 && r<=100 && b<=100 && l<r && t<b);val x=(image.width*l/100).toInt();val y=(image.height*t/100).toInt();bitmap=Bitmap.createBitmap(image,x,y,((r-l)*image.width/100).toInt().coerceAtLeast(1),((b-t)*image.height/100).toInt().coerceAtLeast(1));left="0";top="0";right="100";bottom="100";recognized=null}}) {Text("Apply crop")}
            Button(onClick={run {busy=true;try {recognized=DocumentReader.recognize(image);status="Text recognised. Map the table grid or transcribe bout rows below."} finally {busy=false}}},enabled=!busy) {Text("Recognise text offline")}
            recognized?.let {ocr ->
                Hint(ocr.text.take(1600));Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {Input("Grid rows",rows,{rows=it},true,modifier=Modifier.weight(1f));Input("Grid columns",columns,{columns=it},true,modifier=Modifier.weight(1f))}
                Input("Column edges % · optional, e.g. 0,30,40,50,60,70,80,90,100",columnCuts,{columnCuts=it});Input("Row edges % · optional",rowCuts,{rowCuts=it})
                Button(onClick={run {
                    fun cuts(text: String,count: Int): List<Double> {require(count in 2..32);val v=if(text.isBlank()) (0..count).map {it*100.0/count} else text.split(',').map {it.trim().toDouble()};require(v.size>=3 && v.first()==0.0 && v.last()==100.0 && v.zipWithNext().all {it.first<it.second});return v}
                    val xs=cuts(columnCuts,columns.toInt());val ys=cuts(rowCuts,rows.toInt())
                    val grid=(0 until ys.lastIndex).map {r->(0 until xs.lastIndex).map {c->ocr.words.filter {w->val wx=(w.left+w.right)*50.0/image.width;val wy=(w.top+w.bottom)*50.0/image.height;wx>=xs[c] && wx<xs[c+1] && wy>=ys[r] && wy<ys[r+1]}.sortedBy {it.left}.joinToString(" ") {it.text}}}
                    tableText=Csv.write(grid);status="Grid prepared. Correct names and cells in the table before parsing."
                }}) {Text("Map recognised text into grid")}
            }
        }
        if(bytes!=null && history==null) {
            if(sheets.size>1) Chips(sheets.keys.toList(),"",{tableText=Csv.write(sheets.getValue(it));results=emptyList()})
            Input("Editable table · CSV or tab-separated",tableText,{tableText=it},lines=6)
            Hint("Bout rows: fencer A, fencer B, score A, score B. Poule: header row, names in first column, square score matrix. Remove rankings/totals columns; blanks remain unrecorded.")
            Chips(listOf("Bout rows","Poule matrix"),layout,{layout=it})
            if(layout=="Bout rows") {Input("Column numbers: A, B, score A, score B",mapping,{mapping=it});Row {Checkbox(header,{header=it});Text("First row is a header")}}
            Input("Event / session name",eventName,{eventName=it});Input("Official event date · yyyy-MM-dd",day,{day=it});Chips(listOf("Foil","Épée","Sabre"),weapon,{weapon=it});Input("Target score",target,{target=it},true);Input("Round / poule",round,{round=it})
            Button(onClick={run {val grid=Csv.read(tableText);val mapped=if(layout=="Poule matrix") ResultParser.matrix(grid,target.toInt()) else {val cols=mapping.split(',').map {it.trim().toInt()-1};require(cols.size==4 && cols.all {it>=0});ResultParser.rows(grid,cols[0],cols[1],cols[2],cols[3],header)};require(mapped.size<=1000) {"Import up to 1000 bouts in one batch."};results=mapped.map {ReviewedResult(draft=it)};status="${results.size} candidate bouts. Confirm every included row."}}) {Text("Preview bouts")}
        }
        if(results.isNotEmpty()) {
            Heading("Review ${results.size} results")
            Hint("Exact, unique names reuse profiles. Edit names to match the intended fencer. Unmatched names create new profiles. Resolve every warning; no scores are inferred from standings.")
            TextButton(onClick={results=results.map {if(it.draft.issue.isBlank() && it.draft.a.isNotBlank() && it.draft.b.isNotBlank()) it.copy(reviewed=true) else it}}) {Text("Confirm all rows without warnings")}
            results.forEachIndexed {index,row->
                val d=row.draft
                fun update(next: ReviewedResult) {results=results.toMutableList().also {it[index]=next}}
                Panel("Bout ${index+1}${d.sourceCell.takeIf {it.isNotBlank()}?.let {" · cells $it"}.orEmpty()}") {
                    Row {Checkbox(row.include,{update(row.copy(include=it))});Text("Include")}
                    PersonInput("Fencer A",s,d.a,{update(row.copy(draft=d.copy(a=it),reviewed=false,aId=null))},row.aId,{update(row.copy(draft=d.copy(a=it.name),aId=it.id,reviewed=false))},row.bId)
                    PersonInput("Fencer B",s,d.b,{update(row.copy(draft=d.copy(b=it),reviewed=false,bId=null))},row.bId,{update(row.copy(draft=d.copy(b=it.name),bId=it.id,reviewed=false))},row.aId)
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {Input("A score",d.scoreA?.toString().orEmpty(),{update(row.copy(draft=d.copy(scoreA=it.toIntOrNull()),reviewed=false))},true,modifier=Modifier.weight(1f));Input("B score",d.scoreB?.toString().orEmpty(),{update(row.copy(draft=d.copy(scoreB=it.toIntOrNull()),reviewed=false))},true,modifier=Modifier.weight(1f))}
                    Chips(listOf("auto","A","B","Draw","Unknown"),d.outcome,{update(row.copy(draft=d.copy(outcome=it),reviewed=false))})
                    if(d.issue.isNotBlank()) Text(d.issue,color=MaterialTheme.colorScheme.error)
                    val pa=s.fencers.find {it.id==row.aId} ?: s.fencers.filter {normalizedName(it.name)==normalizedName(d.a)}.singleOrNull();val pb=s.fencers.find {it.id==row.bId} ?: s.fencers.filter {normalizedName(it.name)==normalizedName(d.b)}.singleOrNull()
                    Hint("A: ${pa?.let {"existing ${it.name}"} ?: "new profile / alias match"} · B: ${pb?.let {"existing ${it.name}"} ?: "new profile / alias match"}")
                    val candidates=s.bouts.filter {b->!b.deleted && b.stamp.day==day && ((b.a==pa?.id && b.b==pb?.id && b.scoreA==d.scoreA && b.scoreB==d.scoreB) || (b.b==pa?.id && b.a==pb?.id && b.scoreB==d.scoreA && b.scoreA==d.scoreB))}
                    if(candidates.isNotEmpty()) {Hint("Possible existing result. Keep separate for a rematch, or link to retain its personal details.");FilterChip(row.linkTo.isBlank(),onClick={update(row.copy(linkTo=""))},label={Text("Keep as separate bout")});candidates.take(5).forEach {b->FilterChip(row.linkTo==b.id,onClick={update(row.copy(linkTo=b.id))},label={Text("Link ${s.sessions.find {it.id==b.sessionId}?.name} · ${b.id.takeLast(6)}")})}}
                    Row {Checkbox(row.reviewed,{update(row.copy(reviewed=it))});Text("Names, scores and outcome checked")}
                }
            }
            Button(onClick={run {busy=true;try {
                LocalDate.parse(day);val raw=bytes ?: error("Choose a source file.");val hash=digest(raw);require(!repeat || allowRepeat);val selected=results.filter {it.include};require(selected.isNotEmpty() && selected.all {it.reviewed});require(selected.mapNotNull {it.linkTo.takeIf(String::isNotBlank)}.distinct().size==selected.count {it.linkTo.isNotBlank()}) {"Two rows cannot link to the same bout."}
                val batchId=newId();val sessionId=newId();val attachmentId=hash
                withContext(Dispatchers.IO) {val file=repo.sourceFile(attachmentId);file.parentFile!!.mkdirs();file.writeBytes(raw)}
                repo.change {current ->
                    val people=current.fencers.toMutableList();val bouts=current.bouts.toMutableList()
                    selected.forEach {reviewed->val d=reviewed.draft
                        fun person(name: String,id: String?): Fencer {val p=resolvePerson(current.copy(fencers=people),name,id);if(people.none {it.id==p.id}) people.add(p);return p}
                        val a=person(d.a,reviewed.aId);val b=person(d.b,reviewed.bId)
                        val origin=Origin("Tournament import","${hash}:${page}:${d.row}",batchId,"date","calendar",raw="${d.a} ${d.scoreA} / ${d.b} ${d.scoreB}",attachmentId=attachmentId)
                        val bout=Bout(sessionId=sessionId,a=a.id,b=b.id,scoreA=d.scoreA,scoreB=d.scoreB,outcome=d.outcome,target=target.toInt(),stamp=Stamp("",Instant.now().toString(),ZoneId.systemDefault().id,day,true),round=round,origin=origin)
                        bout.validate()
                        if(reviewed.linkTo.isBlank()) bouts.add(bout) else {val i=bouts.indexOfFirst {it.id==reviewed.linkTo};require(i>=0);bouts[i]=bouts[i].copy(links=bouts[i].links+origin)}
                    }
                    val session=Session(sessionId,eventName,Stamp("",Instant.now().toString(),ZoneId.systemDefault().id,day,true),context="Competition",weapon=weapon,target=target.toInt(),origin=Origin("Tournament import",batchId=batchId,precision="date",dateBasis="calendar"))
                    current.copy(fencers=people,bouts=bouts,sessions=if(bouts.any {it.sessionId==sessionId}) current.sessions+session else current.sessions,batches=current.batches+ImportBatch(batchId,filename,hash,count=selected.size),attachments=if(current.attachments.any {it.id==attachmentId}) current.attachments else current.attachments+SourceAttachment(attachmentId,filename,sourceMime,attachmentId,batchId))
                };results=emptyList();status="Reviewed results imported. Personal results remain separate from other fencers' bouts."
            } finally {busy=false}}},enabled=!busy && results.filter {it.include}.isNotEmpty() && results.filter {it.include}.all {it.reviewed} && (!repeat || allowRepeat),modifier=Modifier.fillMaxWidth()) {Text("Import reviewed results")}
        }
        Heading("Import history")
        s.batches.sortedByDescending {it.createdAt}.forEach {b->Panel(b.name) {
            Hint("${b.count} records · ${b.createdAt}")
            TextButton(onClick={expandedBatch=if(expandedBatch==b.id) "" else b.id}) {Text("Review batch & sources")}
            if(expandedBatch==b.id) {
                val batchBouts=s.bouts.filter {!it.deleted && (it.origin.batchId==b.id || it.links.any {link->link.batchId==b.id})}
                batchBouts.take(30).forEach {bout->BoutRow(s,bout) {reviewBout=bout}}
                if(batchBouts.size>30) Hint("First 30 of ${batchBouts.size} bouts shown. All records are available in History.")
                val sourceIds=batchBouts.flatMap {listOf(it.origin.attachmentId)+it.links.map {link->link.attachmentId}}.toSet()
                s.attachments.filter {it.batchId==b.id || it.id in sourceIds}.forEach {source->TextButton(onClick={run {
                    val sourceUri=FileProvider.getUriForFile(context,"${context.packageName}.files",repo.sourceFile(source.id))
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(sourceUri,source.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),"Open original source"))
                }}) {Text("Open ${source.name}")}}
            }
            TextButton(onClick={undo=b}) {Text("Undo this import")}
        }}
    }
    undo?.let {batch->AlertDialog(onDismissRequest={undo=null},title={Text("Undo ${batch.name}?")},text={Text("Remove records created by this import and its links. Existing bouts that were linked keep their original details. Fencer profiles are retained.")},confirmButton={TextButton(onClick={run {repo.undoBatch(batch.id);undo=null;status="Import undone."}}) {Text("Undo import")}},dismissButton={TextButton(onClick={undo=null}) {Text("Cancel")}}) }
    reviewBout?.let {bout->BoutForm(s,bout,onClose={reviewBout=null},onSave={value,people->run {repo.change {it.copy(fencers=people.fold(it.fencers) {list,p->list.replace(p) {id}},bouts=it.bouts.replace(value) {id})};reviewBout=null}},onDelete={value->run {repo.saveBout(value.copy(deleted=true));reviewBout=null}})}
}
