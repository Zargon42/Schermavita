package dev.riposte.app

import android.content.Context
import androidx.room.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.*
import dev.riposte.core.*
import dev.riposte.fencing.*
import dev.riposte.life.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.*
import java.time.*
import java.io.*
import java.util.zip.*

@kotlinx.serialization.Serializable data class Snapshot(
    val version: Int = 2, val fencers: List<Fencer> = listOf(Fencer("me","Me",true)),
    val sessions: List<Session> = emptyList(), val bouts: List<Bout> = emptyList(), val days: List<TrackingDay> = emptyList(),
    val metrics: List<Metric> = defaultMetrics(), val observations: List<Observation> = emptyList(), val reviews: List<DailyReview> = emptyList(),
    val batches: List<ImportBatch> = emptyList(), val attachments: List<SourceAttachment> = emptyList(), val settings: Settings = Settings(),
    val lessons: List<Lesson> = emptyList()
) {
    val me get() = fencers.single { it.isMe }.id
    fun name(id: String) = fencers.find { it.id==id }?.name ?: "Unknown fencer"
    fun validate() {
        require(version==2) { "Unsupported backup version." }
        require(fencers.count { it.isMe }==1) { "Exactly one personal profile is required." }
        fun unique(ids: List<String>) { require(ids.size==ids.toSet().size) { "Duplicate record identifiers." } }
        unique(fencers.map { it.id }); unique(sessions.map { it.id }); unique(bouts.map { it.id }); unique(lessons.map { it.id }); unique(metrics.map { it.id }); unique(observations.map { it.id }); unique(attachments.map { it.id })
        val people=fencers.map { it.id }.toSet(); val groups=sessions.map { it.id }.toSet(); val definitions=metrics.map { it.id }.toSet()
        bouts.forEach { require(it.a in people && it.b in people && it.a!=it.b && it.sessionId in groups) { "A bout references an invalid participant or session." } }
        lessons.forEach { require(it.sessionId in groups) { "A lesson references an invalid session." }; it.validate() }
        observations.forEach { require(it.metric in definitions) { "Unknown metric ${it.metric}" }; require(it.value?.isFinite()!=false); if(it.stamp.day.isNotBlank()) LocalDate.parse(it.stamp.day) }
        days.forEach { require(Instant.parse(it.start)<Instant.parse(it.end)); ZoneId.of(it.zone); LocalDate.parse(it.label) }
        attachments.forEach { require(it.id.matches(Regex("[a-zA-Z0-9_-]{8,100}"))) { "Invalid attachment name." } }
        require(settings.sessionTrackers.size==3) { "Invalid session tracker definitions." }
        settings.sessionTrackers.forEach { it.validate() }
        unique(settings.equipmentCategories.map {it.id})
        require(settings.equipmentCategories.all {it.name.isNotBlank()})
        sessions.forEach { session ->
            require(session.target in 1..999)
            session.preparation.forEach { (key,answer) ->
                require(key in listOf("willingness","warmup","transport"));answer.definition.validate()
                require(if(answer.definition.kind=="Rating") answer.definition.levels.any {it.value==answer.value.toDoubleOrNull()} else answer.value in answer.definition.choices)
            }
        }
        metrics.filterNot {it.id.startsWith("legacy:")}.forEach {it.definition().validate();it.revisions.forEach {r->r.validate()}}
        settings.boundaryRules.forEach { require(it.minutes in 0..1439); Instant.parse(it.from) }
    }
}
@Entity(tableName="records",indices=[Index("kind"),Index("day"),Index("batch")])
data class Record(@PrimaryKey val key: String,val kind: String,val day: String,val batch: String,val payload: String)
@Dao interface RecordDao {
    @Query("SELECT * FROM records") suspend fun all(): List<Record>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun put(rows: List<Record>)
    @Query("DELETE FROM records WHERE `key` IN (:keys)") suspend fun remove(keys: List<String>)
}
@Database(entities=[Record::class],version=1,exportSchema=false)
abstract class TrackerDb: RoomDatabase() { abstract fun records(): RecordDao }
private val Context.uiPrefs by preferencesDataStore("display")
class AppRepository(val context: Context): FencingRepository {
    private val db=Room.databaseBuilder(context,TrackerDb::class.java,"riposte.db").build()
    private val lock=Mutex()
    private val mutable=MutableStateFlow(Snapshot())
    val state=mutable.asStateFlow()
    val ready=MutableStateFlow(false)
    val tab=context.uiPrefs.data.map { it[intPreferencesKey("tab")] ?: 0 }
    suspend fun selectTab(value: Int) { context.uiPrefs.edit { it[intPreferencesKey("tab")]=value } }
    private inline fun <reified T> record(kind: String,id: String,value: T,day: String="",batch: String="") = Record("$kind/$id",kind,day,batch,codec.encodeToString(value))
    private fun <T> changed(items: List<T>,old: List<T>?,key: (T)->String): List<T> {
        if(items===old) return emptyList()
        if(old.isNullOrEmpty()) return items
        val previous=old.associateBy(key);return items.filter {previous[key(it)]!=it}
    }
    private fun records(s: Snapshot,old: Snapshot?=null): List<Record> = buildList {
        if(s.settings!=old?.settings) add(record("settings","main",s.settings))
        addAll(changed(s.fencers,old?.fencers) {it.id}.map { record("fencer",it.id,it) }); addAll(changed(s.sessions,old?.sessions) {it.id}.map { record("session",it.id,it,it.stamp.day,it.origin.batchId) })
        addAll(changed(s.lessons,old?.lessons) {it.id}.map { record("lesson",it.id,it) })
        addAll(changed(s.bouts,old?.bouts) {it.id}.map { record("bout",it.id,it,it.stamp.day,it.origin.batchId) }); addAll(changed(s.days,old?.days) {it.id}.map { record("day",it.id,it,it.label) })
        addAll(changed(s.metrics,old?.metrics) {it.id}.map { record("metric",it.id,it) }); addAll(changed(s.observations,old?.observations) {it.id}.map { record("observation",it.id,it,it.stamp.day,it.origin.batchId) })
        addAll(changed(s.reviews,old?.reviews) {it.id}.map { record("review",it.id,it,it.day) }); addAll(changed(s.batches,old?.batches) {it.id}.map { record("batch",it.id,it) }); addAll(changed(s.attachments,old?.attachments) {it.id}.map { record("attachment",it.id,it,batch=it.batchId) })
    }
    private fun keys(s: Snapshot): Set<String> = (listOf("settings/main")+s.fencers.map {"fencer/${it.id}"}+s.sessions.map {"session/${it.id}"}+s.lessons.map {"lesson/${it.id}"}+s.bouts.map {"bout/${it.id}"}+s.days.map {"day/${it.id}"}+s.metrics.map {"metric/${it.id}"}+s.observations.map {"observation/${it.id}"}+s.reviews.map {"review/${it.id}"}+s.batches.map {"batch/${it.id}"}+s.attachments.map {"attachment/${it.id}"}).toSet()
    suspend fun load() = withContext(Dispatchers.IO) { lock.withLock {
        val all=db.records().all()
        if(all.isNotEmpty()) {
            fun kind(k: String)=all.filter { it.kind==k }.map { it.payload }
            mutable.value=Snapshot(fencers=kind("fencer").map { codec.decodeFromString(it) },sessions=kind("session").map { codec.decodeFromString(it) },
                bouts=kind("bout").map { codec.decodeFromString(it) },lessons=kind("lesson").map { codec.decodeFromString(it) },days=kind("day").map { codec.decodeFromString(it) },metrics=kind("metric").map { codec.decodeFromString(it) },
                observations=kind("observation").map { codec.decodeFromString(it) },reviews=kind("review").map { codec.decodeFromString(it) },batches=kind("batch").map { codec.decodeFromString(it) },attachments=kind("attachment").map { codec.decodeFromString(it) },settings=kind("settings").firstOrNull()?.let { codec.decodeFromString(it) } ?: Settings())
        } else db.records().put(records(mutable.value))
        ready.value=true
    } }
    suspend fun change(transform: (Snapshot)->Snapshot) = withContext(Dispatchers.IO) { lock.withLock {
        check(ready.value); val old=mutable.value; val next=transform(old); next.validate()
        val removed=keys(old)-keys(next);val updates=records(next,old)
        db.withTransaction {
            removed.chunked(500).forEach { db.records().remove(it) }
            updates.chunked(250).forEach {db.records().put(it)}
        }; mutable.value=next
    } }
    suspend fun ensureDay(at: Instant=Instant.now()): TrackingDay {
        var result: TrackingDay?=null
        change { s -> val d=DayClock.day(at,ZoneId.systemDefault(),s.settings.boundaryRules,s.days); result=d; if(s.days.any { it.id==d.id }) s else s.copy(days=s.days+d) }
        return result!!
    }
    override suspend fun saveSession(session: Session) { require(session.target>0); change { it.copy(sessions=it.sessions.replace(session) { id },settings=it.settings.copy(activeSession=session.id)) } }
    suspend fun closeSession(id: String) { change { s ->
        if(s.settings.activeSession!=id) s else s.copy(settings=s.settings.copy(activeSession=""))
    } }
    override suspend fun saveBout(bout: Bout) { bout.validate(); change { it.copy(bouts=it.bouts.replace(bout) { id }) } }
    override suspend fun saveFencer(fencer: Fencer) { require(fencer.name.isNotBlank()); change { it.copy(fencers=it.fencers.replace(fencer) { id }) } }
    suspend fun mergeFencers(from: String,to: String) { require(from!=to); change { s ->
        val original=s.fencers.first { it.id==from }; val target=s.fencers.first { it.id==to }
        require(!original.isMe) { "Keep your personal profile." }
        require(s.bouts.none { it.contains(from) && it.contains(to) }) { "These fencers have bouts against one another; resolve those records before merging." }
        s.copy(fencers=s.fencers.filter { it.id!=from }.map { if(it.id==to) it.copy(aliases=(it.aliases+original.aliases+original.name).distinct()) else it },
            bouts=s.bouts.map { it.copy(a=if(it.a==from) to else it.a,b=if(it.b==from) to else it.b) })
    } }
    fun sourceFile(id: String): File { require(id.matches(Regex("[a-zA-Z0-9_-]{8,100}"))); return File(context.filesDir,"sources/$id") }
    suspend fun undoBatch(id: String) { change { s ->
        val kept=s.bouts.filter { it.origin.batchId!=id }.map { it.copy(links=it.links.filter { p->p.batchId!=id }) }
        val observations=s.observations.filter {it.origin.batchId!=id}
        val referenced=(kept.flatMap {listOf(it.origin.attachmentId)+it.links.map {p->p.attachmentId}}+observations.map {it.origin.attachmentId}).toSet()
        s.copy(bouts=kept,sessions=s.sessions.filter { it.origin.batchId!=id || kept.any { b->b.sessionId==it.id } },observations=observations,batches=s.batches.filter { it.id!=id },attachments=s.attachments.filter { it.batchId!=id || it.id in referenced })
    } }
}
fun <T> List<T>.replace(item: T,key: T.()->String): List<T> = if(any { it.key()==item.key() }) map { if(it.key()==item.key()) item else it } else this+item

object BackupArchive {
    fun pack(snapshot: Snapshot,file: (String)->File): ByteArray {
        val out=ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("snapshot.json")); z.write(codec.encodeToString(snapshot).toByteArray()); z.closeEntry()
            snapshot.attachments.forEach { a -> val source=file(a.id); require(source.exists()) { "Source attachment ${a.name} is missing." }; z.putNextEntry(ZipEntry("sources/${a.id}")); source.inputStream().use { it.copyTo(z) }; z.closeEntry() }
        }; return out.toByteArray()
    }
    data class Contents(val snapshot: Snapshot,val sources: Map<String,ByteArray>)
    fun unpack(bytes: ByteArray): Contents {
        val entries=mutableMapOf<String,ByteArray>(); var total=0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { z -> while(true) { val e=z.nextEntry ?: break
            require(e.name=="snapshot.json" || e.name.matches(Regex("sources/[a-zA-Z0-9_-]{8,100}"))) { "Invalid backup path." }
            require(!entries.containsKey(e.name)); val data=z.readBounded(100_000_001); total+=data.size; require(total<=250_000_000 && data.size<=100_000_000) { "Backup is too large." }; entries[e.name]=data
        } }
        val decoded=codec.decodeFromString<Snapshot>(entries["snapshot.json"]?.decodeToString() ?: error("Backup has no records.")); require(decoded.version in 1..2) {"Unsupported backup version."}; val s=decoded.copy(version=2); s.validate()
        s.attachments.forEach { require(entries.containsKey("sources/${it.id}")) { "Missing source attachment." } }
        return Contents(s,entries.filterKeys { it.startsWith("sources/") }.mapKeys { it.key.removePrefix("sources/") })
    }
}
