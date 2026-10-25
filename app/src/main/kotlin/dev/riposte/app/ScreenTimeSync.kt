package dev.riposte.app

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.os.UserManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.riposte.core.*
import dev.riposte.life.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.*

fun usageAccess(context: Context): Boolean = context.getSystemService(AppOpsManager::class.java)
    .checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED

suspend fun syncScreenTime(repo: AppRepository) = withContext(Dispatchers.IO) {
    if (!repo.state.value.settings.screenTimeEnabled) return@withContext
    if (!usageAccess(repo.context)) {
        repo.change { it.copy(settings=it.settings.copy(screenTimeStatus="Usage access not granted. Existing readings are retained.")) }
        return@withContext
    }
    if (!repo.context.getSystemService(UserManager::class.java).isUserUnlocked) return@withContext
    val now=Instant.now()
    val from=now.minus(Duration.ofDays(3))
    val stream=repo.context.getSystemService(UsageStatsManager::class.java).queryEvents(from.toEpochMilli(),now.toEpochMilli())
        ?: return@withContext
    val events=mutableListOf<ScreenTime.Event>()
    val event=UsageEvents.Event()
    while(stream.hasNextEvent()) {
        stream.getNextEvent(event)
        val kind=when(event.eventType) {
            UsageEvents.Event.SCREEN_INTERACTIVE -> ScreenTime.Kind.ON
            UsageEvents.Event.SCREEN_NON_INTERACTIVE -> ScreenTime.Kind.OFF
            UsageEvents.Event.KEYGUARD_HIDDEN -> ScreenTime.Kind.UNLOCK
            UsageEvents.Event.KEYGUARD_SHOWN -> ScreenTime.Kind.LOCK
            UsageEvents.Event.DEVICE_SHUTDOWN, UsageEvents.Event.DEVICE_STARTUP -> ScreenTime.Kind.RESET
            else -> null
        }
        if(kind!=null) events.add(ScreenTime.Event(event.timeStamp,kind))
    }
    val snapshot=repo.state.value
    val today=DayClock.day(now,ZoneId.systemDefault(),snapshot.settings.boundaryRules,snapshot.days)
    val previous=DayClock.day(Instant.parse(today.start).minusNanos(1),ZoneId.systemDefault(),snapshot.settings.boundaryRules,snapshot.days)
    val days=listOf(today,previous).distinctBy {it.id}
    val readings=days.mapNotNull { day ->
        val start=Instant.parse(day.start); val end=minOf(now,Instant.parse(day.end))
        if(start<from || end<=start) return@mapNotNull null
        val reading=ScreenTime.measure(events,start.toEpochMilli(),end.toEpochMilli())
        val span=Duration.between(start,end).toMillis()
        if(reading.unknownMilliseconds==span) return@mapNotNull null
        val partial=reading.unknownMilliseconds>0
        Observation("usage-screen-${day.id}","screen_time",Stamp(end.toString(),now.toString(),day.zone,day.label),
            reading.milliseconds/60000.0,
            text=if(partial) "Partial reading: ${reading.unknownMilliseconds/60000} minutes have unknown screen state. Excluded from trend averages." else "Interactive, unlocked screen time. Current day is measured up to the last sync; totals may differ from Digital Wellbeing.",
            scope="aggregate",unit="min",scale="unlocked-screen-minutes",origin=Origin("Android Usage Access",uncertain=partial),endAt=end.toString())
    }
    repo.change { current ->
        // Permission can be disabled while the Android query is in progress.
        if(!current.settings.screenTimeEnabled) current else {
            val kept=readings.filter { fresh -> current.observations.none {it.id==fresh.id && ((!it.origin.uncertain && fresh.origin.uncertain) || it.stamp.createdAt>fresh.stamp.createdAt)} }
            current.copy(days=(current.days+days).distinctBy {it.id},
                metrics=if(current.metrics.any {it.id=="screen_time"}) current.metrics else current.metrics+Metric("screen_time","Screen time","Duration","min",0.0,2880.0,"unlocked-screen-minutes",hidden=true,order=current.metrics.size),
                observations=kept.fold(current.observations) { rows,row->rows.replace(row) {id} },
                settings=current.settings.copy(lastScreenTimeSync=now.toString(),screenTimeStatus=if(readings.isEmpty()) "No usable recent screen events. Missing time remains unknown." else "Recent screen-time readings refreshed. Partial readings are labelled."))
        }
    }
}

class ScreenTimeWorker(context: Context,params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        val repo=(applicationContext as TrackerApp).repository
        repo.ready.first {it}
        return try {syncScreenTime(repo);Result.success()} catch(e: kotlinx.coroutines.CancellationException) {throw e} catch(_: Exception) {Result.retry()}
    }
}
