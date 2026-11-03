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

fun observationSourceLabel(basis: String): String = when(basis) {
    "calendar" -> "Historical calendar dates"
    "tracking" -> "Tracking-day dates"
    else -> "$basis dates"
}

private fun metricScaleLabel(metric: Metric,definition: Metric): String =
    definition.name.takeUnless {it==metric.name} ?: definition.unit.ifBlank {definition.kind}

private data class EquipmentSetup(
    val sword: String,
    val glove: String,
    val lenses: Boolean?,
    val extras: List<Pair<String,String>>
) {
    fun label(): String = buildList {
        if(sword.isNotBlank()) add("Sword: $sword")
        if(glove.isNotBlank()) add("Glove: $glove")
        lenses?.let { add(if(it) "Lenses" else "No lenses") }
        extras.forEach {(category,value)->add("$category: $value")}
    }.joinToString(" · ")
}

@Composable private fun EquipmentInsights(bouts: List<Bout>,s: Snapshot) {
    val setups=bouts.mapNotNull {bout ->
        val details=if(bout.a==s.me) bout.detailsA else bout.detailsB
        val extras=details.equipment.filterValues {it.isNotBlank()}.map {(id,value)->
            (s.settings.equipmentCategories.find {it.id==id}?.name ?: id) to value
        }.sortedBy {it.first}
        val setup=EquipmentSetup(details.sword,details.glove,details.lenses,extras)
        setup.takeIf {it.label().isNotBlank()}?.let {it to bout}
    }.groupBy({it.first},{it.second})
        .map { (setup,rows)->setup to statistics(rows,s.me) }
        .sortedWith(compareByDescending<Pair<EquipmentSetup,FencingStats>> {it.second.bouts}.thenBy {it.first.label()})

    Heading("Equipment cards")
    if(setups.isEmpty()) {
        Hint("No bout equipment has been recorded yet.")
        return
    }
    Hint("Each card summarizes bouts with that recorded setup. Comparisons are descriptive, not proof that equipment caused a result.")
    setups.forEachIndexed {index,(setup,stats)->
        Panel("Setup ${index+1} · ${setup.label()}") {
            Hint("${stats.bouts} bouts · ${stats.wins}W / ${stats.losses}L")
            Hint("Touch share ${stats.touchShare?.let { "${decimal(it*100)}%" } ?: "—"} · win rate ${stats.winRate?.let { "${decimal(it*100)}%" } ?: "—"}")
        }
    }
    if(setups.size>1) {
        val dots=setups.flatMapIndexed {index,(setup,stats)->
            buildList {
                stats.touchShare?.let {add(ChartDot(index+1.0,it*100,"Touch share","Setup ${index+1} · ${setup.label()}: ${decimal(it*100)}% touch share · ${stats.bouts} bouts"))}
                stats.winRate?.let {add(ChartDot(index+1.0,it*100,"Win rate","Setup ${index+1} · ${setup.label()}: ${decimal(it*100)}% win rate · ${stats.bouts} bouts"))}
            }
        }
        Panel("Performance by equipment setup") {
            Hint("Each point is a setup card above; compare touch share and win rate. Small samples and different opponents or contexts can affect results.")
            ObservationChart(dots,"Equipment setup (card number)","Percent",zero=true)
        }
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
    samples.groupBy {it.scale}.forEach { (scale,rows) ->
        val definition=metric.atScale(scale)
        val scaleLabel=metricScaleLabel(metric,definition)
        Panel("${measure.label} vs ${metric.name} · $scaleLabel scale") {
            Hint("Scatter plot: each dot pairs one fencing-day measure (Y) with ${metric.name} (X). Color distinguishes the fencing measure and date source.")
            ObservationChart(rows.map {ChartDot(it.lifeValue,it.performance,"${it.series} · ${observationSourceLabel(it.basis)}",
                "Fencing ${it.fencingDay}: ${it.series} ${decimal(it.performance)} ${measure.unit} · ${it.bouts} bouts; ${metric.name} ${it.lifeDay}: ${decimal(it.lifeValue)} ${definition.unit} · ${observationSourceLabel(it.basis)} · ${definition.name}")},
                "${metric.name} (${definition.unit.ifBlank {scaleLabel}})","${measure.label} (${measure.unit})",measure==PerformanceMeasure.ADJUSTED)
            Hint("${rows.size} paired days · $scaleLabel scale · date sources are color-coded")
            rows.groupBy {it.basis to it.series}.forEach { (sourceSeries,seriesRows)->
                val c=compare(seriesRows.map {DailyValue(it.fencingDay,it.lifeValue,it.basis,it.scale,"daily",1)},
                    seriesRows.map {DailyValue(it.fencingDay,it.performance,it.basis,"performance","daily",1)},false)
                if(seriesRows.size>=20 && c.correlation!=null)
                    Text("${sourceSeries.second} · ${observationSourceLabel(sourceSeries.first)} correlation: ${decimal(c.correlation)}")
                else Hint("${sourceSeries.second} · ${observationSourceLabel(sourceSeries.first)}: ${if(seriesRows.size<20) "collect at least 20 paired days for correlation" else "correlation unavailable for constant values"}")
            }
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
                if(points.isNotEmpty()) {
                    Hint("Timeline: each dot is one recorded day. Color distinguishes date source; gaps mean no recorded result.")
                    ObservationChart(points.map {ChartDot(LocalDate.parse(it.day).toEpochDay().toDouble(),it.value,"${it.series} · ${observationSourceLabel(it.basis)}",
                        "${it.day} · ${it.series}: ${decimal(it.value)} ${it.unit} · ${it.count} bouts · ${observationSourceLabel(it.basis)}")},
                        "Recorded date",measure.unit,measure==PerformanceMeasure.ADJUSTED,dateAxis=true)
                }
                if(points.isEmpty()) Hint("No eligible observations for this measure and range.")
                if(measure==PerformanceMeasure.ADJUSTED) Hint("${bouts.count {strength?.comparisons?.get(it.id)?.provisional!=false}} bouts excluded: provisional or unavailable baseline.")
            }
            Panel("Fencing performance vs life metric") {
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
            EquipmentInsights(bouts,s)
            Heading("Tactical observations")
            s.settings.tacticalTags.forEach {tag->val worked=bouts.count {tag in (if(it.a==s.me) it.detailsA else it.detailsB).strengths};val improve=bouts.count {tag in (if(it.a==s.me) it.detailsA else it.detailsB).improve};if(worked+improve>0) Hint("$tag · $worked strengths · $improve to work on")}
            Hint("Equipment and tactical comparisons are descriptive. Opponents, formats, and context can differ.")
        } else {
            MetricPicker("Trend",numeric,x,{x=it});val xm=numeric.find {it.id==x}
            if(xm!=null) {
                ScreenTimeChartHint(s,xm)
                val xs=dailyValues(s.observations,xm).filter {dates(it.day)}
                if(xs.isEmpty()) Hint("No matching life observations.")
                xs.groupBy {it.scale}.forEach { (scale,rows) ->
                    val definition=xm.atScale(scale)
                    val scaleLabel=metricScaleLabel(xm,definition)
                    Panel("${xm.name} timeline · $scaleLabel") {
                        Hint("Timeline: each dot is one observed day. Color distinguishes historical calendar dates from tracking-day dates.")
                        ObservationChart(rows.map {ChartDot(LocalDate.parse(it.day).toEpochDay().toDouble(),it.value,observationSourceLabel(it.basis),
                            "${it.day}: ${decimal(it.value)} ${definition.unit} · ${it.count} observations · ${observationSourceLabel(it.basis)} · $scaleLabel")},
                            "Recorded date",definition.unit.ifBlank {scaleLabel},dateAxis=true)
                        Hint("${rows.size} observed days · scale $scaleLabel")
                        rows.groupBy {it.basis}.forEach { (basis,sourceRows)->
                            Hint("${observationSourceLabel(basis)} · ${sourceRows.count {it.scope=="daily"}} daily reviews; ${sourceRows.count {it.scope=="momentary"}} days summarised from events")
                            sourceRows.groupBy {LocalDate.parse(it.day).with(java.time.DayOfWeek.MONDAY).toString()}.toSortedMap(reverseOrder()).entries.take(8).forEach { (week,values)->Hint("${observationSourceLabel(basis)} · week of $week · ${values.size}/7 days · mean ${decimal(values.map {it.value}.average())} ${definition.unit}")}
                        }
                    }
                }
                MetricPicker("Compare with",numeric,y,{y=it});Row {Checkbox(preceding,{preceding=it});Text("First factor on the preceding day")}
                val ym=numeric.find {it.id==y}
                if(ym!=null) {
                    ScreenTimeChartHint(s,ym)
                    val ys=dailyValues(s.observations,ym).filter {dates(it.day)}
                    xs.groupBy {it.scale}.forEach { (xscale,a)->ys.groupBy {it.scale}.forEach { (yscale,b)->
                        val xdefinition=xm.atScale(xscale);val ydefinition=ym.atScale(yscale)
                        val xscaleLabel=metricScaleLabel(xm,xdefinition);val yscaleLabel=metricScaleLabel(ym,ydefinition)
                        val paired=a.mapNotNull {v->
                            val day=LocalDate.parse(v.day).plusDays(if(preceding) 1 else 0).toString()
                            b.find {it.day==day && it.basis==v.basis}?.let {v to it}
                        }
                        if(paired.isNotEmpty()) Panel("${xm.name} vs ${ym.name} · $xscaleLabel / $yscaleLabel scales") {
                            Hint("Scatter plot: each dot pairs ${xm.name} (X) with ${ym.name} (Y). Color identifies the date source.")
                            ObservationChart(paired.map {(first,second)->ChartDot(first.value,second.value,observationSourceLabel(first.basis),
                                "${first.day} ${xm.name}: ${decimal(first.value)} ${xdefinition.unit}; ${second.day} ${ym.name}: ${decimal(second.value)} ${ydefinition.unit} · ${observationSourceLabel(first.basis)}")},
                                "${xm.name} (${xdefinition.unit.ifBlank {xscaleLabel}})","${ym.name} (${ydefinition.unit.ifBlank {yscaleLabel}})")
                            val c=compare(a,b,preceding)
                            Hint("${paired.size} paired days · $xscaleLabel / $yscaleLabel scales · date sources are color-coded")
                            if(paired.size>=20 && c.correlation!=null) Text("Descriptive correlation: ${decimal(c.correlation)}") else Hint("Collect at least 20 paired days with varying values before showing a correlation summary.")
                        }
                    }}
                }
            }
        }
        Hint("Missing days and incompatible date bases are excluded. Associations do not establish causes; charts do not forecast health or performance.")
    }
}
