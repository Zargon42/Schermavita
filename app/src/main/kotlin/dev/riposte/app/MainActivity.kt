@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package dev.riposte.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.riposte.core.*
import dev.riposte.fencing.*
import dev.riposte.life.*
import kotlinx.coroutines.*
import java.time.*

class MainActivity: ComponentActivity() {
    private val action=mutableStateOf("")
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); enableEdgeToEdge(); action.value=intent.action.orEmpty()
        setContent { RiposteTheme { TrackerRoot((application as TrackerApp).repository,action.value) { action.value="" } } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); action.value=intent.action.orEmpty() }
}
@Composable fun RiposteTheme(content: @Composable ()->Unit) {
    val dark=isSystemInDarkTheme()
    MaterialTheme(colorScheme=if(dark) darkColorScheme(primary=Color(0xFFA8D4B8),secondary=Color(0xFFDFC48A),background=Color(0xFF101C18),surface=Color(0xFF17231F)) else lightColorScheme(primary=Color(0xFF22684E),secondary=Color(0xFF89682F),background=Color(0xFFF7F8F2),surface=Color(0xFFF7F8F2),primaryContainer=Color(0xFFDCECD9),secondaryContainer=Color(0xFFF3E8CD)),content=content)
}
@Composable fun TrackerRoot(repo: AppRepository,action: String,consume: ()->Unit) {
    val appName=stringResource(R.string.app_name)
    val s by repo.state.collectAsStateWithLifecycle(); val ready by repo.ready.collectAsStateWithLifecycle()
    val tab by repo.tab.collectAsStateWithLifecycle(initialValue=0)
    var page by remember { mutableStateOf("") }; var person by remember { mutableStateOf("") }
    var editSession by remember { mutableStateOf<Session?>(null) }; var sessionForm by remember { mutableStateOf(false) }
    var editBout by remember { mutableStateOf<Bout?>(null) }; var boutForm by remember { mutableStateOf(false) }
    var metric by remember { mutableStateOf<Metric?>(null) }; var event by remember { mutableStateOf<Observation?>(null) }
    var review by remember { mutableStateOf(false) }; var notice by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }; var currentDay by remember { mutableStateOf("") }; val lifecycle=LocalLifecycleOwner.current.lifecycle
    val scope=rememberCoroutineScope(); val snackbar=remember { SnackbarHostState() }
    val run: ((suspend ()->Unit)->Unit) = { task -> scope.launch { try { task() } catch(e: Exception) { if(e is CancellationException) throw e; snackbar.showSnackbar(e.message ?: "Could not complete this action") } } }
    fun addBout() { if(s.sessions.none { it.id==s.settings.activeSession }) { editSession=null; sessionForm=true } else { editBout=null; boutForm=true } }
    LaunchedEffect(ready,lifecycle) { if(ready) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        suspend fun refreshDay() {
            val state=repo.state.value
            val next=DayClock.day(Instant.now(),ZoneId.systemDefault(),state.settings.boundaryRules,state.days)
            if(state.days.none {it.id==next.id}) repo.ensureDay()
            if(day.isBlank() || day==currentDay) day=next.label
            currentDay=next.label
        }
        refreshDay()
        try { syncScreenTime(repo) } catch(e: Exception) {
            if(e is CancellationException) throw e
            repo.change {it.copy(settings=it.settings.copy(screenTimeStatus=e.message ?: "Screen-time refresh unavailable"))}
        }
        try { syncHealth(repo) } catch(e: Exception) {
            if(e is CancellationException) throw e
            repo.change { it.copy(settings=it.settings.copy(healthStatus=e.message ?: "Refresh unavailable")) }
        }
        while(isActive) { delay(60_000); refreshDay() }
    } }
    LaunchedEffect(action,ready) { if(ready) { when(action) { "dev.riposte.ADD_BOUT"-> { repo.selectTab(1); addBout() }; "dev.riposte.REVIEW"->review=true; "android.intent.action.VIEW_PERMISSION_USAGE","androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE"->page="Privacy" }; consume() } }
    BackHandler(page.isNotEmpty()) { page="" }
    if(!ready) { Box(Modifier.fillMaxSize(),contentAlignment=androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }; return }
    Scaffold(topBar={ TopAppBar(title={ Column { Text(if(page.isEmpty()) appName else page,fontWeight=FontWeight.SemiBold); if(page.isEmpty()) Text("A record of your own",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant) } },
        navigationIcon={ if(page.isNotEmpty()) IconButton(onClick={ page="" }) { Icon(Glyph.ArrowBack,"Back") } },
        actions={ if(page.isEmpty()) { IconButton(onClick={ page="Imports" }) { Icon(Glyph.FileDownload,"Import data") }; IconButton(onClick={ page="Settings" }) { Icon(Glyph.Settings,"Settings") } } }) },
        snackbarHost={ SnackbarHost(snackbar) },bottomBar={ if(page.isEmpty()) NavigationBar {
            val icons=listOf(Glyph.WbSunny,Glyph.Sports,Glyph.History,Glyph.Insights)
            listOf("Today","Fencing","History","Insights").forEachIndexed { i,label -> NavigationBarItem(selected=tab==i,onClick={ run { repo.selectTab(i) } },icon={ Icon(icons[i],label) },label={ Text(label) }) }
        } }) { padding -> Box(Modifier.padding(padding).fillMaxSize()) {
        when(page) {
            "Settings" -> SettingsScreen(repo,s,run) { if(it=="My profile") {person=s.me;page="Fencer"} else page=it }
            "Trackers" -> MetricsScreen(repo,s,run)
            "Imports" -> ImportScreen(repo,s,run)
            "Backup & export" -> BackupScreen(repo,s,run)
            "Privacy" -> ScrollPage { Heading("Your data stays with you"); Text("$appName stores your records on this phone. Steps and sleep are read from Health Connect only with your permission. Document recognition runs on-device. There is no account, analytics service, or automatic cloud backup. You control manual encrypted backups and exports."); Text("Optional screen-time sync reads Android usage events with Usage access. Only tracking-day totals and coverage information are stored, not per-app usage lists. Turn off screen-time sync or revoke Usage access to stop new reads; saved readings remain in your history and backups."); Text("Revoking Health Connect access stops new reads. Previously imported readings remain in your history. Synced readings can reappear on refresh after deletion; disable their integration first to stop new reads.") }
            "Fencer" -> ProfileScreen(repo,s,person,run,onBout={ editBout=it; boutForm=true })
            else -> when(tab) {
                0 -> TodayScreen(s,day,{ day=it },onMetric={ metric=it; event=null },onReview={ review=true },onBout={ addBout() },onEvent={ metric=s.metrics.find { m->m.id==it.metric }; event=it },onDay={ changed->run { repo.change { it.copy(days=it.days.replace(changed) { id }) } } })
                1 -> FencingScreen(repo,s,run,onSession={ editSession=it; sessionForm=true },onBout={ editBout=it; boutForm=true },onAdd={ addBout() },onPerson={ person=it; page="Fencer" })
                2 -> HistoryScreen(s,onEvent={ metric=s.metrics.find { m->m.id==it.metric }; event=it },onBout={ editBout=it; boutForm=true })
                3 -> InsightsScreen(s)
            }
        }
    } }
    if(sessionForm) SessionForm(s,editSession,onClose={sessionForm=false},onSave={ value,categories,lessons -> run { repo.change { it.copy(sessions=it.sessions.replace(value) {id},lessons=it.lessons.filterNot {it.sessionId==value.id}+lessons,settings=it.settings.copy(activeSession=value.id,equipmentCategories=categories)) }; sessionForm=false } })
    if(boutForm) BoutForm(s,editBout,onClose={boutForm=false},onSave={ value,people -> run { value.validate(); repo.change { it.copy(fencers=people.fold(it.fencers) { list,p->list.replace(p) { id } },bouts=it.bouts.replace(value) { id },sessions=if(editBout==null) it.sessions.map {session->if(session.id==value.sessionId) session.copy(target=value.target ?: session.target) else session} else it.sessions) }; boutForm=false } },onDelete={ value -> run { repo.saveBout(value.copy(deleted=true)); boutForm=false; if(snackbar.showSnackbar("Bout removed",actionLabel="Undo")==SnackbarResult.ActionPerformed) repo.saveBout(value) } })
    metric?.let { m -> EventForm(s,m,event,day,onClose={metric=null},onSave={ value->run { repo.change { it.copy(observations=it.observations.replace(value) { id }) }; metric=null } },onDelete={ value->run { repo.change { it.copy(observations=it.observations.replace(value.copy(deleted=true)) { id }) }; metric=null; if(snackbar.showSnackbar("Entry removed",actionLabel="Undo")==SnackbarResult.ActionPerformed) repo.change { it.copy(observations=it.observations.replace(value) { id }) } } }) }
    if(review) ReviewForm(s,day,onClose={review=false},onSave={ values,reviewRecord -> run {
        repo.change { current ->
            val reviewedMetrics=current.metrics.filter { !it.hidden && (it.kind in listOf("Rating","Yes/no") || it.id in listOf("exercise","caffeine")) }.map {it.id}.toSet()
            val retained=current.observations.filterNot { it.id=="review:$day:${it.metric}" && it.metric in reviewedMetrics }
            current.copy(observations=retained+values,reviews=current.reviews.replace(reviewRecord) {id})
        }; review=false
    } })
}
@Composable fun ScrollPage(content: @Composable ColumnScope.()->Unit) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(14.dp),content=content) }
@Composable fun Heading(text: String) { Text(text,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold) }
@Composable fun Hint(text: String) { Text(text,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable fun Panel(title: String,content: @Composable ColumnScope.()->Unit) { Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) { Text(title,fontWeight=FontWeight.SemiBold); content() } } }
@Composable fun Chips(items: List<String>,selected: String,onSelect: (String)->Unit) { FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { items.forEach { FilterChip(selected==it,onClick={onSelect(it)},label={Text(it)}) } } }
@Composable fun Input(label: String,value: String,onChange: (String)->Unit,numeric: Boolean=false,lines: Int=1,modifier: Modifier=Modifier) { OutlinedTextField(value,onChange,label={Text(label)},modifier=modifier.fillMaxWidth(),singleLine=lines==1,minLines=lines,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=if(numeric) androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Text)) }
@Composable fun Form(title: String,onClose: ()->Unit,onSave: (() ->Unit)?=null,content: @Composable ColumnScope.()->Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest=onClose,properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) { Scaffold(topBar={TopAppBar(title={Text(title)},navigationIcon={ IconButton(onClick=onClose) { Icon(Glyph.Close,"Close") } },actions={ if(onSave!=null) TextButton(onClick=onSave) { Text("Save",fontWeight=FontWeight.Bold) } })}) { p -> Column(Modifier.padding(p).imePadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content) } }
    }
}
fun decimal(value: Double?): String = value?.let { if(it%1.0==0.0) it.toInt().toString() else "%.1f".format(java.util.Locale.ROOT,it) } ?: "—"
fun localTime(stamp: Stamp): String = runCatching { Instant.parse(stamp.occurredAt).atZone(ZoneId.of(stamp.zone)).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) }.getOrDefault("")
fun parsedTime(value: String,zone: String=ZoneId.systemDefault().id): Instant = LocalDateTime.parse(value.trim(),java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")).atZone(ZoneId.of(zone)).toInstant()
fun resolvePerson(s: Snapshot,name: String,id: String?): Fencer {
    s.fencers.find { it.id==id }?.let { return it }; require(name.isNotBlank()) { "Enter a fencer's name." }
    val matches=s.fencers.filter { f-> (listOf(f.name)+f.aliases).any { normalizedName(it)==normalizedName(name) } }
    require(matches.size<=1) { "Several fencers share this name. Select the correct profile." }
    return matches.firstOrNull() ?: Fencer(name=name.trim())
}
