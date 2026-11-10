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
    val permissions = healthMetrics.map {it.permissionName()}.toSet()
    fun permissionsFor(enabled: Set<String>)=healthMetrics.filter {it.id in enabled}.map {it.permissionName()}.toSet()
    suspend fun granted() = if(available) client.permissionController.getGrantedPermissions() else emptySet()
    suspend fun read(days: List<TrackingDay>,enabled: Set<String> = setOf("steps","sleep")): List<Observation> {
        val granted=granted(); val result=mutableListOf<Observation>()
        for(day in days) {
            val start=Instant.parse(day.start); val end=minOf(Instant.parse(day.end),Instant.now())
            if(start>=end) continue
            if("steps" in enabled && HealthPermission.getReadPermission(StepsRecord::class) in granted) {
                val total=client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL),TimeRangeFilter.between(start,end)))[StepsRecord.COUNT_TOTAL]
                if(total!=null) result.add(Observation("health-steps-${day.id}","steps",Stamp(end.toString(),Instant.now().toString(),day.zone,day.label),total.toDouble(),scope="aggregate",unit="steps",scale="steps",origin=Origin("Health Connect",dateBasis="tracking")))
            }
            if(enabled.any {it.startsWith("sleep")} && HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) {
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
                    if("sleep" in enabled) result.add(Observation("health-sleep-${s.metadata.id}","sleep",Stamp(s.startTime.toString(),Instant.now().toString(),day.zone,day.label),hours,
                        if(s.stages.isEmpty()) "Session duration; sleep stages unavailable" else "Time asleep",scope="momentary",unit="hours",scale="hours",origin=Origin("Health Connect",s.metadata.id,raw=s.metadata.dataOrigin.packageName),endAt=s.endTime.toString()))
                    mapOf("sleep_deep" to SleepSessionRecord.STAGE_TYPE_DEEP,"sleep_light" to SleepSessionRecord.STAGE_TYPE_LIGHT,"sleep_rem" to SleepSessionRecord.STAGE_TYPE_REM).forEach {(id,stage)->
                        if(id in enabled && s.stages.isNotEmpty()) result.add(Observation("health-$id-${s.metadata.id}",id,Stamp(s.startTime.toString(),Instant.now().toString(),day.zone,day.label),
                            s.stages.filter {it.stage==stage}.sumOf {Duration.between(it.startTime,it.endTime).seconds}/3600.0,scope="momentary",unit="hours",scale=id,origin=Origin("Health Connect",s.metadata.id,raw=s.metadata.dataOrigin.packageName),endAt=s.endTime.toString()))
                    }
                }
            }
            for(metric in healthMetrics.filter {it.id in enabled && it.id!="steps" && !it.id.startsWith("sleep") && it.permissionName() in granted}) {
                val values: List<Pair<String,Double>> = when(metric.id) {
                    "resting_hr"->records<RestingHeartRateRecord>(start,end).map {it.metadata.dataOrigin.packageName to it.beatsPerMinute.toDouble()}
                    "hrv"->records<HeartRateVariabilityRmssdRecord>(start,end).map {it.metadata.dataOrigin.packageName to it.heartRateVariabilityMillis}
                    "respiratory_rate"->records<RespiratoryRateRecord>(start,end).map {it.metadata.dataOrigin.packageName to it.rate}
                    "oxygen"->records<OxygenSaturationRecord>(start,end).map {it.metadata.dataOrigin.packageName to it.percentage.value}
                    else->emptyList()
                }
                val provider=values.map {it.first}.distinct().sortedWith(compareByDescending<String> {it.contains("fitbit",true)}.thenBy {it}).firstOrNull()
                val value=when(metric.id) {
                    "active_calories"->client.aggregate(AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),TimeRangeFilter.between(start,end)))[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
                    "total_calories"->client.aggregate(AggregateRequest(setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),TimeRangeFilter.between(start,end)))[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
                    else->values.filter {it.first==provider}.map {it.second}.takeIf {it.isNotEmpty()}?.average()
                }
                if(value!=null) result.add(Observation("health-${metric.id}-${day.id}",metric.id,Stamp(end.toString(),Instant.now().toString(),day.zone,day.label),value,scope="aggregate",unit=metric.unit,scale=metric.id,origin=Origin("Health Connect",raw=provider ?: "Health Connect aggregate")))
            }
        }
        return result
    }
    private suspend inline fun <reified T: Record> records(start: Instant,end: Instant): List<T> {
        val all=mutableListOf<T>();var token: String?=null
        do {val response=client.readRecords(ReadRecordsRequest(T::class,TimeRangeFilter.between(start,end),pageToken=token));all.addAll(response.records);token=response.pageToken} while(token!=null)
        return all
    }
}
