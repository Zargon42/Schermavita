package dev.riposte.app
import android.app.*
import android.content.*
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import dev.riposte.core.*
import dev.riposte.health.*
import dev.riposte.life.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.*
import java.util.concurrent.TimeUnit

class TrackerApp: Application() {
    lateinit var repository: AppRepository
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    override fun onCreate() { super.onCreate(); repository=AppRepository(this)
        scope.launch { repository.load(); repository.ensureDay(); ReminderWorker.schedule(this@TrackerApp,repository.state.value.settings) }
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("health",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<HealthWorker>(6,TimeUnit.HOURS).build())
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("screen-time",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<ScreenTimeWorker>(1,TimeUnit.HOURS).build())
    }
}
suspend fun syncHealth(repo: AppRepository) {
    val reader=HealthReader(repo.context); if(!reader.available) { repo.change { it.copy(settings=it.settings.copy(healthStatus="Health Connect is unavailable")) }; return }
    val granted=reader.granted(); val s=repo.state.value
    val days=(0..7).map {DayClock.day(Instant.now().minus(Duration.ofDays(it.toLong())),ZoneId.systemDefault(),s.settings.boundaryRules,s.days)}.distinctBy {it.id}
    val events=mutableListOf<Observation>();val statuses=mutableMapOf<String,String>();val synced=mutableMapOf<String,String>();val successful=mutableSetOf<String>()
    for(metric in healthMetrics.filter {it.id in s.settings.healthImports}) {
        if(metric.permissionName() !in granted) {statuses[metric.id]="Permission not granted";continue}
        try {val rows=reader.read(days,setOf(metric.id));events.addAll(rows);successful.add(metric.id);statuses[metric.id]=if(rows.isEmpty()) "No data available" else "Connected";synced[metric.id]=Instant.now().toString()}
        catch(e: kotlinx.coroutines.CancellationException) {throw e}
        catch(e: Exception) {statuses[metric.id]="Read failed; saved history retained"}
    }
    repo.change {current->
        val enabled=successful.intersect(current.settings.healthImports)
        current.copy(days=(current.days+days).distinctBy {it.id},metrics=(current.metrics+healthMetricDefinitions()).distinctBy {it.id},
            observations=mergeHealthReadings(current.observations,events,days.map {it.label}.toSet(),successful,current.settings.healthImports),
            settings=current.settings.copy(lastHealthSync=if(successful.isNotEmpty()) Instant.now().toString() else current.settings.lastHealthSync,healthStatus="Health Connect imports checked",healthMetricStatus=current.settings.healthMetricStatus+statuses,healthMetricSync=current.settings.healthMetricSync+synced))
    }

}
class HealthWorker(context: Context,params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        val repo=(applicationContext as TrackerApp).repository; repo.ready.first { it }
        val reader=HealthReader(applicationContext)
        if("android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND" !in reader.granted()) return Result.success()
        return try { syncHealth(repo); Result.success() } catch(_: Exception) { Result.retry() }
    }
}
class ReminderWorker(context: Context,params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        val repo=(applicationContext as TrackerApp).repository; repo.ready.first { it }; val s=repo.state.value
        if(!s.settings.reminderEnabled) return Result.success()
        val day=DayClock.day(Instant.now(),ZoneId.systemDefault(),s.settings.boundaryRules,s.days).label
        if(s.reviews.none { it.day==day }) {
            val manager=applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("review","Daily review",NotificationManager.IMPORTANCE_DEFAULT))
            val intent=Intent(applicationContext,MainActivity::class.java).setAction("dev.riposte.REVIEW")
            val pending=PendingIntent.getActivity(applicationContext,1,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n=NotificationCompat.Builder(applicationContext,"review").setSmallIcon(R.drawable.ic_riposte).setContentTitle("A moment for your day").setContentText("Review ${day} and fill in anything you want to remember.").setContentIntent(pending).setAutoCancel(true).build()
            if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                NotificationManagerCompat.from(applicationContext).notify(1,n)
            }
        }
        schedule(applicationContext,s.settings); return Result.success()
    }
    companion object {
        fun schedule(context: Context,settings: Settings) {
            val wm=WorkManager.getInstance(context); if(!settings.reminderEnabled) { wm.cancelUniqueWork("review"); return }
            val now=ZonedDateTime.now(); var next=now.toLocalDate().atTime(settings.reminderMinutes/60,settings.reminderMinutes%60).atZone(now.zone)
            if(!next.isAfter(now)) next=next.plusDays(1)
            wm.enqueueUniqueWork("review",ExistingWorkPolicy.REPLACE,OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(Duration.between(now,next).seconds,TimeUnit.SECONDS).build())
        }
    }
}
