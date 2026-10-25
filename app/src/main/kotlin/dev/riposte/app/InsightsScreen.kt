package dev.riposte.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.riposte.core.*
import dev.riposte.fencing.*
import dev.riposte.life.*
import dev.riposte.insights.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.*

data class ChartDot(val x: Double,val y: Double,val series: String,val detail: String)

@Composable fun ObservationChart(dots: List<ChartDot>,xAxis: String,yAxis: String,zero: Boolean=false,dateAxis: Boolean=false) {
    var regression by remember {mutableStateOf(false)}
    var selected by remember(dots) {mutableStateOf<Int?>(null)}
    val colors=listOf(MaterialTheme.colorScheme.primary,MaterialTheme.colorScheme.tertiary,MaterialTheme.colorScheme.secondary)
    val groups=dots.groupBy {it.series}
    val lines=if(regression) groups.mapValues {regressionLine(it.value.map {p->p.x to p.y})} else emptyMap()
    val allY=dots.map {it.y}+lines.values.flatten().map {it.second}+if(zero) listOf(0.0) else emptyList()
    val observedLo=allY.minOrNull() ?: 0.0; val observedHi=allY.maxOrNull() ?: 1.0
    val lo=if(observedLo==observedHi) observedLo-.5 else observedLo
    val hi=if(observedLo==observedHi) observedHi+.5 else observedHi
    val minX=dots.minOfOrNull {it.x} ?: 0.0; val maxX=dots.maxOfOrNull {it.x} ?: 1.0
    fun position(x: Double,y: Double,w: Float,h: Float) = Offset(
        if(minX==maxX) w/2 else (12+(x-minX)/(maxX-minX)*(w-24)).toFloat(),
        (h-12-(y-lo)/(hi-lo)*(h-24)).toFloat())
    FilterChip(regression,onClick={regression=!regression},label={Text("Regression line")})
    Text("Y: $yAxis · ${decimal(lo)} to ${decimal(hi)}",style=MaterialTheme.typography.labelMedium)
    if(dots.isEmpty()) Hint("No matching observations. Try a wider range or different filters.")
    Canvas(Modifier.fillMaxWidth().height(170.dp)
        .semantics {contentDescription="$yAxis by $xAxis; ${dots.size} observations. Use Inspect first point and Next point for values."}
        .pointerInput(dots,lo,hi) {detectTapGestures {tap ->
            selected=dots.indices.minByOrNull {i->(position(dots[i].x,dots[i].y,size.width.toFloat(),size.height.toFloat())-tap).getDistance()}
        }}) {
        drawLine(Color.Gray.copy(alpha=.4f),Offset(12f,size.height-12),Offset(size.width-12,size.height-12))
        if(zero && dots.isNotEmpty()) {
            val y=position(minX,0.0,size.width,size.height).y
            drawLine(Color.Gray,Offset(12f,y),Offset(size.width-12,y),strokeWidth=2f)
        }
        groups.keys.forEachIndexed {i,name ->
            val color=colors[i%colors.size]
            val line=lines[name].orEmpty()
            if(line.size==2) drawLine(color.copy(alpha=.7f),position(line.first().first,line.first().second,size.width,size.height),position(line.last().first,line.last().second,size.width,size.height),strokeWidth=2f)
            dots.forEachIndexed {index,p->if(p.series==name) drawCircle(color,if(selected==index) 7f else 4f,position(p.x,p.y,size.width,size.height))}
        }
    }
    val xRange=if(dateAxis) "${LocalDate.ofEpochDay(minX.toLong())} — ${LocalDate.ofEpochDay(maxX.toLong())}" else "${decimal(minX)} to ${decimal(maxX)}"
    Text("X: $xAxis${if(dots.isEmpty()) "" else " · $xRange"}",style=MaterialTheme.typography.labelMedium)
    groups.keys.forEachIndexed {i,name->Text("● $name",color=colors[i%colors.size],style=MaterialTheme.typography.labelMedium)}
    if(dots.isNotEmpty()) {
        Row {
            TextButton(onClick={selected=if(selected==null) 0 else (selected!!-1).coerceAtLeast(0)}) {Text(if(selected==null) "Inspect first point" else "Previous point")}
            TextButton(onClick={selected=if(selected==null) 0 else (selected!!+1).coerceAtMost(dots.lastIndex)}) {Text("Next point")}
        }
        selected?.let {Hint(dots[it].detail)}
    }
}

@Composable fun MetricPicker(label: String,metrics: List<Metric>,selected: String,onSelect: (String)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    Box {OutlinedButton(onClick={expanded=true}) {Text("$label: ${metrics.find {it.id==selected}?.name ?: "Choose"}")}
        DropdownMenu(expanded,{expanded=false}) {metrics.forEach {m->DropdownMenuItem(text={Text(m.name)},onClick={onSelect(m.id);expanded=false})}} }
}

@Composable fun PerformancePicker(selected: PerformanceMeasure,onSelect: (PerformanceMeasure)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    Box {OutlinedButton(onClick={expanded=true}) {Text("Performance: ${selected.label}")}
        DropdownMenu(expanded,{expanded=false}) {PerformanceMeasure.entries.forEach {m->DropdownMenuItem(text={Text(m.label)},onClick={onSelect(m);expanded=false})}} }
}

fun screenTimeChartNote(s: Snapshot,now: Instant = Instant.now()): String? {
    val day=DayClock.day(now,ZoneId.systemDefault(),s.settings.boundaryRules,s.days).label
    val latest=s.observations.filter {!it.deleted && !it.origin.uncertain && it.metric=="screen_time" && it.stamp.day==day}
        .maxByOrNull {it.stamp.createdAt} ?: return null
    val measured=latest.endAt ?: latest.stamp.occurredAt
    val local=runCatching {Instant.parse(measured).atZone(ZoneId.of(latest.stamp.zone)).toLocalDateTime().toString().replace('T',' ')}.getOrDefault(measured)
    return "$day: ${decimal(latest.value)} min so far · measured through $local"
}

@Composable fun ScreenTimeChartHint(s: Snapshot,metric: Metric) {
    if(metric.id=="screen_time") {
        screenTimeChartNote(s)?.let {Hint(it)}
        if(s.observations.none {!it.deleted && !it.origin.uncertain && it.metric=="screen_time" && it.value!=null})
            Hint("No complete screen-time readings yet. Enable sync and grant Usage access in Settings > Screen time. Partial readings are excluded.")
        Hint("Interactive, unlocked screen time · minutes. Saved readings remain available when sync is off.")
    }
}

@Composable fun MatchedChart(samples: List<MatchedSample>,metric: Metric,measure: PerformanceMeasure) {
    if(samples.isEmpty()) Hint("No matching days. Missing values and incompatible date bases are excluded.")
    samples.groupBy {Triple(it.basis,it.scale,it.series)}.forEach { (key,rows) ->
        val definition=metric.atScale(key.second)
        Panel("${key.third} × ${metric.name} · ${key.first}") {
            ObservationChart(rows.map {ChartDot(it.lifeValue,it.performance,key.third,
                "Fencing ${it.fencingDay}: ${decimal(it.performance)} ${measure.unit} · ${it.bouts} bouts; ${metric.name} ${it.lifeDay}: ${decimal(it.lifeValue)} ${definition.unit} · ${key.first} · ${key.second}")},
                "${metric.name} (${definition.unit.ifBlank {key.second}})","${key.third} (${measure.unit})",measure==PerformanceMeasure.ADJUSTED)
            Hint("${rows.size} paired days · scale ${key.second}")
            val c=compare(rows.map {DailyValue(it.fencingDay,it.lifeValue,it.basis,it.scale,"daily",1)},
                rows.map {DailyValue(it.fencingDay,it.performance,it.basis,"performance","daily",1)},false)
            if(rows.size>=20 && c.correlation!=null) Text("Descriptive correlation: ${decimal(c.correlation)}")
            else Hint(if(rows.size<20) "Collect at least 20 paired days before showing a correlation summary." else "Correlation unavailable for constant values.")
        }
    }
}

@Composable fun InsightsScreen(s: Snapshot) {
    var section by remember {mutableStateOf("Fencing")};var from by remember {mutableStateOf("")};var to by remember {mutableStateOf("")}
    var target by remember {mutableStateOf("All")};var context by remember {mutableStateOf("All")};var weapon by remember {mutableStateOf("All")};var opponent by remember {mutableStateOf("")}
    var x by remember {mutableStateOf("mood")};var y by remember {mutableStateOf("stress")};var preceding by remember {mutableStateOf(false)}
    var measure by remember {mutableStateOf(PerformanceMeasure.TOUCH_SHARE)}
    val numeric=remember(s.metrics) {chartableMetrics(s.metrics)}
    val filter=AnalysisFilter(weapon,target,context)
    val strength by produceState<StrengthAnalysis?>(null,s.bouts,s.sessions,s.fencers,filter) {
        value=null
        value=withContext(Dispatchers.Default) {opponentStrength(s.bouts,s.sessions,s.fencers,filter)}
    }
    fun dates(day: String) = validDay(day) && (from.isBlank() || day>=from) && (to.isBlank() || day<=to)
    ScrollPage {
        Heading("Patterns, with context");Chips(listOf("Fencing","Life"),section,{section=it})
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {Input("From date",from,{from=it},modifier=Modifier.weight(1f));Input("To date",to,{to=it},modifier=Modifier.weight(1f))}
        if((from.isNotBlank() && !validDay(from)) || (to.isNotBlank() && !validDay(to)) || (from.isNotBlank() && to.isNotBlank() && from>to)) Hint("Enter a valid date range using yyyy-MM-dd.")
        if(section=="Fencing") {
            Chips(listOf("All","Foil","Épée","Sabre"),weapon,{weapon=it})
            Chips(listOf("All","5","10","15"),target,{target=it});Chips(listOf("All","Training","Competition"),context,{context=it});Input("Opponent",opponent,{opponent=it})
            val bouts=remember(s.bouts,s.sessions,filter,from,to,opponent,s.fencers) {
                analysisBouts(s.bouts,s.sessions,filter).filter {b->b.contains(s.me) && dates(b.stamp.day) && (opponent.isBlank() || s.name(if(b.a==s.me)b.b else b.a).contains(opponent,true))}
            }
            StatsPanel(bouts,s.me)
            val stats=statistics(bouts,s.me)
            Panel("Performance across recorded days") {
                Text("Points for : against: ${stats.pointRatio} · W/L: ${stats.winLoss}")
                Text("Self eval: ${if(stats.execution!=null) "${decimal(stats.execution)} / 5" else "Not rated"}")
                PerformancePicker(measure) {measure=it}
                val points=remember(bouts,measure,strength) {performancePoints(bouts,s.me,measure,strength ?: StrengthAnalysis())}
                if(strength==null && measure==PerformanceMeasure.ADJUSTED) LinearProgressIndicator(Modifier.fillMaxWidth())
                points.groupBy {it.basis}.forEach { (basis,rows) ->
                    Hint("$basis · ${rows.map {it.day}.distinct().size} recorded days · ${rows.first().day} — ${rows.last().day}")
                    ObservationChart(rows.map {ChartDot(LocalDate.parse(it.day).toEpochDay().toDouble(),it.value,it.series,"${it.day} · ${it.series}: ${decimal(it.value)} ${it.unit} · ${it.count} bouts · $basis")},"Recorded date",measure.unit,measure==PerformanceMeasure.ADJUSTED,dateAxis=true)
                }
                if(points.isEmpty()) Hint("No eligible observations for this measure and range.")
                if(measure==PerformanceMeasure.ADJUSTED) Hint("${bouts.count {strength?.comparisons?.get(it.id)?.provisional!=false}} bouts excluded: provisional or unavailable baseline.")
                Hint("Each dot is one observed day. Missing days are not filled.")
            }
            Panel("Compare with life") {
                MetricPicker("Life metric",numeric,x,{x=it})
                Row {Checkbox(preceding,{preceding=it});Text("Life metric on the previous day")}
                val metric=numeric.find {it.id==x}
                if(metric!=null) {
                    ScreenTimeChartHint(s,metric)
                    val points=remember(bouts,measure,strength) {performancePoints(bouts,s.me,measure,strength ?: StrengthAnalysis())}
                    val life=remember(s.observations,metric) {dailyValues(s.observations,metric)}
                    MatchedChart(matchPerformance(points,life,preceding),metric,measure)
                }
            }
            Panel("Opponent baselines") {
                Hint("Score-based estimates from recorded results, separate by weapon. Prior days only; at least 5 earlier scored bouts per participant for an established comparison. These are not official levels or win probabilities.")
                if(strength==null) LinearProgressIndicator(Modifier.fillMaxWidth())
                val ids=bouts.map {it.id}.toSet()
                val comparisons=strength?.comparisons?.values.orEmpty().filter {it.boutId in ids}
                if(comparisons.isEmpty() && strength!=null) Hint("No eligible scored bouts for opponent estimates.")
                comparisons.groupBy {it.weapon to if(it.a==s.me) it.b else it.a}.toSortedMap(compareBy<Pair<String,String>> {it.first}.thenBy {s.name(it.second)}).forEach { (key,rows) ->
                    val rating=strength?.ratings?.get(key) ?: RatingEvidence()
                    val established=rows.filterNot {it.provisional};val total=established.sumOf {it.touches}
                    val excluded=rows.size-established.size
                    Text("${s.name(key.second)} · ${key.first}",style=MaterialTheme.typography.titleSmall)
                    Hint("Strength ${decimal(rating.rating)} · ${rating.bouts} supporting bouts${if(rating.bouts<5) " · provisional" else ""} · latest available estimate")
                    if(total>0) Hint("Actual ${decimal(established.sumOf {it.actual(s.me)*it.touches}*100/total)}% / expected ${decimal(established.sumOf {it.expected(s.me)*it.touches}*100/total)}% touch share · ${established.size} compared bouts")
                    if(excluded>0) Hint("$excluded provisional comparisons excluded")
                }
            }
            Heading("By equipment")
            bouts.groupBy {if(it.a==s.me) it.detailsA.sword else it.detailsB.sword}.filterKeys {it.isNotBlank()}.forEach { (sword,rows)->val equipmentStats=statistics(rows,s.me);Hint("$sword · ${rows.size} bouts · ${decimal(equipmentStats.touchShare?.times(100))}% touch share · ${equipmentStats.wins}W / ${equipmentStats.losses}L")}
            Heading("Tactical observations")
            s.settings.tacticalTags.forEach {tag->val worked=bouts.count {tag in (if(it.a==s.me) it.detailsA else it.detailsB).strengths};val improve=bouts.count {tag in (if(it.a==s.me) it.detailsA else it.detailsB).improve};if(worked+improve>0) Hint("$tag · $worked strengths · $improve to work on")}
            Hint("Equipment and tactical comparisons are descriptive. Opponents, formats, and context can differ.")
        } else {
            MetricPicker("Trend",numeric,x,{x=it});val xm=numeric.find {it.id==x}
            if(xm!=null) {
                ScreenTimeChartHint(s,xm)
                val xs=dailyValues(s.observations,xm).filter {dates(it.day)}
                if(xs.isEmpty()) Hint("No matching life observations.")
                xs.groupBy {it.basis to it.scale}.forEach { (key,rows) ->
                    val definition=xm.atScale(key.second)
                    Panel("${xm.name} · ${key.first}") {
                        ObservationChart(rows.map {ChartDot(LocalDate.parse(it.day).toEpochDay().toDouble(),it.value,xm.name,"${it.day}: ${decimal(it.value)} ${definition.unit} · ${it.count} observations · ${it.basis} · ${it.scale}")},"Recorded date",definition.unit.ifBlank {key.second},dateAxis=true)
                        Hint("${rows.size} observed days · scale ${key.second} · average ${decimal(rows.map {it.value}.average())} ${definition.unit}")
                        Hint("${rows.count {it.scope=="daily"}} daily reviews; ${rows.count {it.scope=="momentary"}} days summarised from events")
                        rows.groupBy {LocalDate.parse(it.day).with(java.time.DayOfWeek.MONDAY).toString()}.toSortedMap(reverseOrder()).entries.take(8).forEach { (week,values)->Hint("Week of $week · ${values.size}/7 days · mean ${decimal(values.map {it.value}.average())} ${definition.unit}")}
                    }
                }
                MetricPicker("Compare with",numeric,y,{y=it});Row {Checkbox(preceding,{preceding=it});Text("First factor on the preceding day")}
                val ym=numeric.find {it.id==y}
                if(ym!=null) {
                    ScreenTimeChartHint(s,ym)
                    val ys=dailyValues(s.observations,ym).filter {dates(it.day)}
                    xs.groupBy {it.basis to it.scale}.forEach { (kx,a)->ys.groupBy {it.basis to it.scale}.filterKeys {it.first==kx.first}.forEach { (ky,b)->
                        val paired=a.mapNotNull {v->val day=LocalDate.parse(v.day).plusDays(if(preceding) 1 else 0).toString();b.find {it.day==day}?.let {v to it}}
                        if(paired.isNotEmpty()) Panel("${xm.name} × ${ym.name} · ${kx.first}") {
                            ObservationChart(paired.map {(first,second)->ChartDot(first.value,second.value,ym.name,"${first.day} ${xm.name}: ${decimal(first.value)} ${xm.atScale(kx.second).unit}; ${second.day} ${ym.name}: ${decimal(second.value)} ${ym.atScale(ky.second).unit}")},"${xm.name} (${xm.atScale(kx.second).unit.ifBlank {kx.second}})","${ym.name} (${ym.atScale(ky.second).unit.ifBlank {ky.second}})")
                            val c=compare(a,b,preceding)
                            Hint("${paired.size} paired days · ${kx.second} / ${ky.second}")
                            if(paired.size>=20 && c.correlation!=null) Text("Descriptive correlation: ${decimal(c.correlation)}") else Hint("Collect at least 20 paired days with varying values before showing a correlation summary.")
                        }
                    }}
                }
            }
        }
        Hint("Missing days and incompatible date bases are excluded. Associations do not establish causes; charts do not forecast health or performance.")
    }
}
