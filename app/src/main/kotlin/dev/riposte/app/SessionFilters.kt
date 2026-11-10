package dev.riposte.app

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.compose.runtime.*
import dev.riposte.fencing.*

fun sessionFilterValues(s: Snapshot,b: Bout): Map<String,String> {
    val session=s.sessions.find {it.id==b.sessionId} ?: return emptyMap()
    val d=if(b.a==s.me) b.detailsA else b.detailsB
    return buildMap {
        put("Contact lenses",when(d.lenses){true->"Yes";false->"No";else->"Unknown"})
        put("Venue",session.venue.ifBlank {"Unknown"})
        preparationKeys.forEach {key->val answer=session.preparation[key];put(key,answer?.let {a->a.definition.levels.find {it.value==a.value.toDoubleOrNull()}?.label ?: a.value} ?: "Unknown")}
        equipmentFields(s).forEach {put(it,equipmentValue(s,d,it))}
        session.reflection?.answers?.filter {it.question.kind!="Text"}?.forEach {put("Reflection: ${it.question.title}",it.value.ifBlank {"Unknown"})}
    }
}
fun matchesSessionFilters(s: Snapshot,b: Bout,filters: Map<String,String>): Boolean {
    val values=sessionFilterValues(s,b)
    return filters.all {(key,value)->value=="All" || (values[key] ?: "Unknown")==value}
}
@Composable fun SessionFilters(s: Snapshot,filters: Map<String,String>,onChange: (Map<String,String>)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    TextButton(onClick={expanded=!expanded}) {Text("Session & equipment filters (${filters.count {it.value!="All"}})")}
    if(expanded) {
        val rows=s.bouts.filter {it.contains(s.me) && !it.deleted}.map {sessionFilterValues(s,it)}
        rows.flatMap {it.keys}.distinct().forEach {key->
            Text(s.settings.equipmentCategories.find {it.id==key}?.name ?: when(key){"willingness"->"Willingness to fence";"warmup"->"Warm-up quality";"transport"->"Arrival transport";else->key})
            Chips(listOf("All","Unknown")+rows.mapNotNull {it[key]}.filter {it!="Unknown"}.distinct().sorted(),filters[key] ?: "All",{onChange(filters+(key to it))})
        }
        TextButton(onClick={onChange(emptyMap())}) {Text("Clear session filters")}
    }
}
