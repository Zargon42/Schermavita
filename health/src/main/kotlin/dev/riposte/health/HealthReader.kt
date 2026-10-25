package dev.riposte.health
import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.*
import androidx.health.connect.client.time.TimeRangeFilter
import dev.riposte.core.*
import dev.riposte.life.*
import java.time.*

class HealthReader(private val context: Context) {
    val available get() = HealthConnectClient.getSdkStatus(context)==HealthConnectClient.SDK_AVAILABLE
    private val client get() = HealthConnectClient.getOrCreate(context)
    val permissions = setOf(HealthPermission.getReadPermission(StepsRecord::class),HealthPermission.getReadPermission(SleepSessionRecord::class))
    suspend fun granted() = if(available) client.permissionController.getGrantedPermissions() else emptySet()
    suspend fun read(days: List<TrackingDay>): List<Observation> {
        val granted=granted(); val result=mutableListOf<Observation>()
        for(day in days) {
            val start=Instant.parse(day.start); val end=minOf(Instant.parse(day.end),Instant.now())
            if(start>=end) continue
            if(HealthPermission.getReadPermission(StepsRecord::class) in granted) {
                val total=client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL),TimeRangeFilter.between(start,end)))[StepsRecord.COUNT_TOTAL]
                if(total!=null) result.add(Observation("health-steps-${day.id}","steps",Stamp(end.toString(),Instant.now().toString(),day.zone,day.label),total.toDouble(),scope="aggregate",unit="steps",scale="steps",origin=Origin("Health Connect",dateBasis="tracking")))
            }
            if(HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) {
                val all=mutableListOf<SleepSessionRecord>(); var token: String?=null
                do { val response=client.readRecords(ReadRecordsRequest(SleepSessionRecord::class,TimeRangeFilter.between(start.minus(Duration.ofDays(2)),end),pageToken=token)); all.addAll(response.records); token=response.pageToken } while(token!=null)
                // Prefer Fitbit/Google Health, then remove overlapping source copies. Preserve naps.
                val accepted=mutableListOf<SleepSessionRecord>()
                all.filter { it.endTime>=start && it.endTime<Instant.parse(day.end) }.sortedWith(compareByDescending<SleepSessionRecord> { it.metadata.dataOrigin.packageName.contains("fitbit",true) }.thenByDescending { Duration.between(it.startTime,it.endTime).seconds }).forEach { record ->
                    if(accepted.none { it.startTime<record.endTime && record.startTime<it.endTime }) accepted.add(record)
                }
                for(s in accepted) {
                    val awake=s.stages.filter { it.stage in listOf(SleepSessionRecord.STAGE_TYPE_AWAKE,SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,SleepSessionRecord.STAGE_TYPE_OUT_OF_BED) }.sumOf { Duration.between(it.startTime,it.endTime).seconds }
                    val hours=(Duration.between(s.startTime,s.endTime).seconds-awake)/3600.0
                    result.add(Observation("health-sleep-${s.metadata.id}","sleep",Stamp(s.startTime.toString(),Instant.now().toString(),day.zone,day.label),hours,
                        if(s.stages.isEmpty()) "Session duration; sleep stages unavailable" else "Time asleep",scope="momentary",unit="hours",scale="hours",origin=Origin("Health Connect",s.metadata.id,raw=s.metadata.dataOrigin.packageName),endAt=s.endTime.toString()))
                }
            }
        }
        return result
    }
}
