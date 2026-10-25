package dev.riposte.app
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.riposte.core.*
import dev.riposte.health.*
import androidx.health.connect.client.PermissionController
import java.time.*

@Composable fun SettingsScreen(repo: AppRepository,s: Snapshot,run: (suspend ()->Unit)->Unit,onPage: (String)->Unit) {
    val appName=stringResource(R.string.app_name)
    val context=LocalContext.current;val health=remember {HealthReader(context)}
    val permissions=rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {run {syncHealth(repo)}}
    val notifications=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val cutoff=s.settings.boundaryRules.last().minutes
    var boundary by remember(cutoff) {mutableStateOf("%02d:%02d".format(cutoff/60,cutoff%60))}
    var reminder by remember(s.settings.reminderMinutes) {mutableStateOf("%02d:%02d".format(s.settings.reminderMinutes/60,s.settings.reminderMinutes%60))}
    var tags by remember {mutableStateOf(s.settings.tacticalTags.joinToString(", "))};var saved by remember {mutableStateOf("")}
    ScrollPage {
        Heading("Your rhythm")
        Panel("Tracking day") { Input("New day starts · HH:mm",boundary,{boundary=it});Hint("Entries before this time belong to the preceding day. Changes start after your current tracking day; existing assignments stay unchanged.")
            Button(onClick={run {val t=LocalTime.parse(boundary);repo.change {it.copy(settings=it.settings.copy(boundaryRules=DayClock.change(it.settings.boundaryRules,Instant.now(),ZoneId.systemDefault(),t.hour*60+t.minute,it.days)))};saved="New boundary saved for the next tracking day."}}) {Text("Save day boundary")}
            val next=s.settings.boundaryRules.last();if(Instant.parse(next.from)>Instant.now()) Hint("Takes effect ${Instant.parse(next.from).atZone(ZoneId.systemDefault())}")
        }
        Panel("Daily review reminder") {
            Row {Switch(s.settings.reminderEnabled,{enabled->run {repo.change {it.copy(settings=it.settings.copy(reminderEnabled=enabled))};ReminderWorker.schedule(context,repo.state.value.settings)};if(enabled && Build.VERSION.SDK_INT>=33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)});Text("Remind me")}
            Input("Reminder time · HH:mm",reminder,{reminder=it});Button(onClick={run {val t=LocalTime.parse(reminder);repo.change {it.copy(settings=it.settings.copy(reminderMinutes=t.hour*60+t.minute))};ReminderWorker.schedule(context,repo.state.value.settings);saved="Reminder time saved."};if(Build.VERSION.SDK_INT>=33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)}) {Text("Save reminder")}
            Hint("Android may delay reminders to save battery. If needed, allow notifications and background activity for $appName in HyperOS settings.")
        }
        Panel("Steps & sleep") {
            Text(s.settings.healthStatus);Hint("Last successful sync: ${s.settings.lastHealthSync.ifBlank {"Never"}}")
            Button(onClick={if(health.available) permissions.launch(health.permissions) else saved="Install or update Health Connect in Android settings."}) {Text("Connect Health Connect")}
            TextButton(onClick={if(health.available) permissions.launch(health.permissions+"android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND")}) {Text("Allow background reading")}
            OutlinedButton(onClick={run {syncHealth(repo)}}) {Text("Refresh now")}
            Hint("In Google Health, allow writing steps and sleep to Health Connect. $appName reads them; it does not write health data back.")
        }
        Panel("Screen time") {
            Text(s.settings.screenTimeStatus)
            Hint("Last successful sync: ${s.settings.lastScreenTimeSync.ifBlank {"Never"}}")
            Row { Switch(s.settings.screenTimeEnabled,{ enabled->run {repo.change {it.copy(settings=it.settings.copy(screenTimeEnabled=enabled))};if(enabled) syncScreenTime(repo)}});Text("Sync screen time") }
            Button(onClick={run {
                repo.change {it.copy(settings=it.settings.copy(screenTimeEnabled=true))}
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }}) {Text("Grant Usage access")}
            OutlinedButton(onClick={run {syncScreenTime(repo)}},enabled=s.settings.screenTimeEnabled) {Text("Refresh screen time")}
            Hint("Enable $appName in Android's Usage access settings. Measures interactive, unlocked screen time from recent Android events, using your tracking-day boundary. It does not read Digital Wellbeing's private history; totals can differ. Refreshes when opened and approximately hourly. Partial readings are excluded from trends. Disabling sync retains history.")
        }
        Panel("Fencing tags") {Input("Comma-separated tactical tags",tags,{tags=it},lines=3);Button(onClick={run {repo.change {it.copy(settings=it.settings.copy(tacticalTags=tags.split(',').map {it.trim()}.filter {it.isNotBlank()}.distinct()))};saved="Tags saved. Previous bout tags are retained."}}) {Text("Save tags")}}
        if(saved.isNotBlank()) Text(saved)
        OutlinedButton(onClick={onPage("My profile")},Modifier.fillMaxWidth()) {Text("Your fencer name & aliases")}
        OutlinedButton(onClick={onPage("Trackers")},Modifier.fillMaxWidth()) {Text("Configure trackers")}
        OutlinedButton(onClick={onPage("Backup & export")},Modifier.fillMaxWidth()) {Text("Backup, restore & export")}
        TextButton(onClick={onPage("Privacy")}) {Text("Privacy & permissions")}
        Hint("$appName ${BuildConfig.VERSION_NAME} · personal edition · offline")
    }
}
