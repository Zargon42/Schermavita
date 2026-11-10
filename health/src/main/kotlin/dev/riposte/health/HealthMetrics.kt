package dev.riposte.health

import dev.riposte.life.Metric

data class HealthMetric(val id: String,val name: String,val unit: String,val permission: String)
val healthMetrics=listOf(
    HealthMetric("steps","Steps","steps","STEPS"),HealthMetric("sleep","Sleep duration","hours","SLEEP"),
    HealthMetric("sleep_deep","Deep sleep","hours","SLEEP"),HealthMetric("sleep_light","Light sleep","hours","SLEEP"),HealthMetric("sleep_rem","REM sleep","hours","SLEEP"),
    HealthMetric("resting_hr","Resting heart rate","bpm","RESTING_HEART_RATE"),
    HealthMetric("hrv","Heart-rate variability (RMSSD)","ms","HEART_RATE_VARIABILITY"),
    HealthMetric("respiratory_rate","Respiratory rate","breaths/min","RESPIRATORY_RATE"),
    HealthMetric("oxygen","Oxygen saturation","%","OXYGEN_SATURATION"),
    HealthMetric("active_calories","Active calories","kcal","ACTIVE_CALORIES_BURNED"),
    HealthMetric("total_calories","Total calories","kcal","TOTAL_CALORIES_BURNED")
)
fun HealthMetric.permissionName()="android.permission.health.READ_$permission"
fun healthMetricDefinitions()=healthMetrics.map {Metric(id=it.id,name=it.name,kind=if(it.id.startsWith("sleep")) "Duration" else "Number",unit=it.unit,min=0.0,max=1000000.0,scale=it.id,hidden=true)}
