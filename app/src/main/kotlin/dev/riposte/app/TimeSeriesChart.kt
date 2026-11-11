package dev.riposte.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.riposte.life.*
import dev.riposte.insights.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable fun TimeSeriesChart(dots: List<ChartDot>,yAxis: String,zero: Boolean,yBounds: Pair<Double,Double>?) {
    val lightBackground=MaterialTheme.colorScheme.surface.luminance()>.5f
    fun revisionColor(key: String)=Color.hsv((kotlin.math.abs(key.hashCode().toLong())%360).toFloat(),.65f,if(lightBackground) .6f else .95f)
    val today=LocalDate.now()
    val days=dots.map {ChartDay(LocalDate.ofEpochDay(it.x.toLong()),it.y,it.trendSeries,it.basis,it.revisions,it.detail)}
    val first=days.minOfOrNull {it.day} ?: today
    val last=days.maxOfOrNull {it.day} ?: today
    val identity=dots.map {it.trendSeries to it.basis}.distinct().sortedBy {it.toString()}
    var period by remember(identity) {mutableStateOf(ChartPeriod.MONTH)}
    var anchor by remember(identity) {mutableStateOf(last)}
    var individual by remember(identity) {mutableStateOf(false)}
    var trend by remember {mutableStateOf("Off")}
    var regression by remember {mutableStateOf(false)}
    val window=chartWindow(period,anchor,first,last)
    val bucket=if(individual) ChartBucket.DAY else period.bucket
    val bins=chartBins(days,window,bucket)
    val daily=chartBins(days,ChartWindow(first,last),ChartBucket.DAY)
    val visibleDaily=daily.filter {it.start in window}
    val groups=bins.groupBy {it.series to it.basis}
    val rawGroups=daily.groupBy {it.series to it.basis}
    val smooth=if(trend=="Off") emptyMap() else rawGroups.mapValues {(_,rows)->
        rollingMean(rows.map {it.start.toEpochDay().toDouble() to it.value},trend.toInt())
    }
    val regressions=if(!regression) emptyMap() else visibleDaily.groupBy {it.series to it.basis}.mapValues {(_,rows)->
        regressionLine(rows.map {it.start.toEpochDay().toDouble() to it.value})
    }
    var selected by remember(bins) {mutableStateOf<Int?>(null)}
    val mixedColor=MaterialTheme.colorScheme.onSurfaceVariant
    val axisColor=MaterialTheme.colorScheme.onSurfaceVariant
    fun binColor(bin: ChartBin)=if(bin.revisions.size==1) revisionColor(bin.revisions.keys.first()) else mixedColor
    fun seriesColor(key: Pair<String,String>): Color {
        val revisions=groups[key].orEmpty().flatMap {it.revisions.keys}.distinct()
        return if(revisions.size==1) revisionColor(revisions.first()) else mixedColor
    }
    fun navigate(direction: Int) {
        if(canPageChart(period,anchor,direction,first,last,today)) anchor=pageChart(period,anchor,direction)
    }
    Chips(ChartPeriod.entries.map {it.label},period.label,{label->
        val next=ChartPeriod.entries.first {it.label==label}
        if(next!=period) {if(next.trailingDays>0) anchor=today;period=next;individual=false}
    })
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        TextButton(onClick={navigate(-1)},enabled=canPageChart(period,anchor,-1,first,last,today)) {Text("Previous period")}
        TextButton(onClick={navigate(1)},enabled=canPageChart(period,anchor,1,first,last,today)) {Text("Next period")}
    }
    TextButton(onClick={
        anchor=today
        if(period==ChartPeriod.ALL) {period=ChartPeriod.MONTH;individual=false}
        selected=null
    }) {Text("Jump to today")}
    Text("${window.start} – ${window.end}",style=MaterialTheme.typography.labelMedium)
    if(period.bucket!=ChartBucket.DAY) FilterChip(individual,onClick={individual=!individual},label={Text("Individual days")})
    Text("Trend")
    Chips(listOf("Off","7","14","28"),trend,{trend=it})
    FilterChip(regression,onClick={regression=!regression},label={Text("Regression line")})
    if(trend!="Off") Hint("Dashed: $trend-day rolling mean. Each observed day has equal weight.")
    if(regression) Hint("Dotted: linear regression of visible daily values.")
    if(days.any {it.basis=="calendar"}) Hint("Thin solid lines: tracking-day dates. Thin dashed lines: historical calendar dates.")
    smooth.forEach {(key,points)->points.lastOrNull {it.day>=window.start.toEpochDay() && it.day<=window.end.toEpochDay()}?.let {
        Hint("${key.first} · ${observationSourceLabel(key.second)}: $trend-day mean ${decimal(it.value)} · ${it.count} observed days")
    }}
    val trendY=smooth.values.flatten().filter {it.day>=window.start.toEpochDay() && it.day<=window.end.toEpochDay()}.map {it.value}
    val allY=bins.map {it.value}+trendY+regressions.values.flatten().map {it.second}+if(zero) listOf(0.0) else emptyList()
    val lower=yBounds?.first ?: allY.minOrNull() ?: 0.0
    val upper=yBounds?.second ?: allY.maxOrNull() ?: 1.0
    val lo=if(lower==upper) lower-.5 else lower
    val hi=if(lower==upper) upper+.5 else upper
    val minX=window.start.toEpochDay().toDouble();val maxX=window.end.toEpochDay().toDouble()
    val density=LocalDensity.current
    val left=with(density) {44.dp.toPx()};val right=with(density) {12.dp.toPx()}
    val bottom=with(density) {28.dp.toPx()};val top=with(density) {14.dp.toPx()}
    fun x(bin: ChartBin)=(bin.start.toEpochDay()+bin.end.toEpochDay())/2.0
    fun position(day: Double,value: Double,w: Float,h: Float): Offset = Offset(
        if(maxX==minX) (left+w-right)/2 else (left+(day-minX)/(maxX-minX)*(w-left-right)).toFloat(),
        (h-bottom-(value-lo)/(hi-lo)*(h-bottom-top)).toFloat())
    Text("Y: $yAxis · ${decimal(lo)} to ${decimal(hi)}",style=MaterialTheme.typography.labelMedium)
    if(bins.isEmpty()) Hint("No matching observations in this period.")
    Canvas(Modifier.fillMaxWidth().height(200.dp)
        .semantics {contentDescription="$yAxis timeline; ${period.label}; ${bins.size} displayed values; ${if(individual) "individual days" else "line chart"}. Use Inspect first point and Next point for values."}
        .pointerInput(bins,window,lo,hi) {detectTapGestures {tap->selected=bins.indices.minByOrNull {i->
            (position(x(bins[i]),bins[i].value,size.width.toFloat(),size.height.toFloat())-tap).getDistance()
        }}}
        .pointerInput(period,anchor,first,last,today) {
            var distance=0f
            detectHorizontalDragGestures(onDragStart={distance=0f},onHorizontalDrag={change,amount->change.consume();distance+=amount},
                onDragEnd={if(kotlin.math.abs(distance)>48.dp.toPx()) navigate(if(distance<0) 1 else -1)})
        }) {
        val paint=android.graphics.Paint().apply {color=android.graphics.Color.argb(255,(axisColor.red*255).toInt(),(axisColor.green*255).toInt(),(axisColor.blue*255).toInt());textSize=10.dp.toPx();isAntiAlias=true}
        listOf(lo,(lo+hi)/2,hi).forEach {value->
            val y=position(minX,value,size.width,size.height).y
            drawLine(axisColor.copy(alpha=.18f),Offset(left,y),Offset(size.width-right,y))
            drawContext.canvas.nativeCanvas.drawText(decimal(value),0f,y-3,paint)
        }
        val tickDays=if(period==ChartPeriod.WEEK || period==ChartPeriod.LAST_7) (0L..6L).map {window.start.plusDays(it)}
            else listOf(window.start,LocalDate.ofEpochDay((window.start.toEpochDay()+window.end.toEpochDay())/2),window.end).distinct()
        tickDays.forEach {day->
            val label=day.format(DateTimeFormatter.ofPattern(if(period==ChartPeriod.WEEK || period==ChartPeriod.LAST_7) "EE" else if(period==ChartPeriod.YEAR || period==ChartPeriod.ALL) "MMM yy" else "d MMM"))
            val px=position(day.toEpochDay().toDouble(),lo,size.width,size.height).x
            drawContext.canvas.nativeCanvas.drawText(label,(px-paint.measureText(label)/2).coerceIn(0f,(size.width-paint.measureText(label)).coerceAtLeast(0f)),size.height-2,paint)
        }
        clipRect(left-3.dp.toPx(),0f,size.width-right+3.dp.toPx(),size.height-bottom+3.dp.toPx()) {
            // Observations first; overlays and selection are deliberately separate passes.
            groups.values.forEach {rows->
                val connections=if(individual) emptyList() else rows.zipWithNext().filter {connectChartBins(it.first,it.second,bucket)}
                connections.forEach {(a,b)->
                    val from=position(x(a),a.value,size.width,size.height);val to=position(x(b),b.value,size.width,size.height)
                    val middle=(from+to)/2f
                    val sourceStyle=if(a.basis=="calendar") PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(),2.dp.toPx())) else null
                    drawLine(binColor(a),from,middle,strokeWidth=1.5.dp.toPx(),pathEffect=sourceStyle)
                    drawLine(binColor(b),middle,to,strokeWidth=1.5.dp.toPx(),pathEffect=sourceStyle)
                }
                rows.forEach {bin->if(individual || connections.none {it.first==bin || it.second==bin}) {
                    val pos=position(x(bin),bin.value,size.width,size.height);val r=2.dp.toPx()
                    if(bin.basis=="calendar") drawRect(binColor(bin),pos-Offset(r,r),androidx.compose.ui.geometry.Size(r*2,r*2)) else drawCircle(binColor(bin),r,pos)
                }}
            }
            regressions.forEach {(key,line)->if(line.size==2) drawLine(seriesColor(key),position(line.first().first,line.first().second,size.width,size.height),
                position(line.last().first,line.last().second,size.width,size.height),strokeWidth=2.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(),3.dp.toPx())))}
            smooth.forEach {(key,points)->points.zipWithNext().filter {it.second.day-it.first.day<=trend.toInt()}.forEach {(a,b)->
                drawLine(seriesColor(key),position(a.day,a.value,size.width,size.height),position(b.day,b.value,size.width,size.height),
                    strokeWidth=2.5.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(),4.dp.toPx())))
            }}
            selected?.let {i->val bin=bins[i];drawCircle(binColor(bin),4.dp.toPx(),position(x(bin),bin.value,size.width,size.height),style=Stroke(1.dp.toPx()))}
        }
    }
    Text("${visibleDaily.size} observed days · ${bins.size} displayed values",style=MaterialTheme.typography.labelMedium)
    dots.filter {LocalDate.ofEpochDay(it.x.toLong()) in window}.groupBy {it.series}.forEach {(name,rows)->
        val sample=rows.first()
        Text("● $name",color=if(sample.revisions.size==1) revisionColor(sample.revisions.first()) else mixedColor,style=MaterialTheme.typography.labelMedium)
        Hint("${LocalDate.ofEpochDay(rows.minOf {it.x}.toLong())} – ${LocalDate.ofEpochDay(rows.maxOf {it.x}.toLong())}")
    }
    if(bins.any {it.revisions.size>1}) Text("Multiple compatible revisions",color=mixedColor,style=MaterialTheme.typography.labelMedium)
    if(bins.isNotEmpty()) {
        Row {
            TextButton(onClick={selected=if(selected==null) 0 else (selected!!-1).coerceAtLeast(0)}) {Text(if(selected==null) "Inspect first point" else "Previous point")}
            TextButton(onClick={selected=if(selected==null) 0 else (selected!!+1).coerceAtMost(bins.lastIndex)}) {Text("Next point")}
        }
        selected?.let {i->val bin=bins[i]
            if(bucket==ChartBucket.DAY) bin.details.forEach {Hint(it)} else {
                Hint("${bin.start} – ${bin.end}: mean ${decimal(bin.value)} · ${bin.count} observed days · ${observationSourceLabel(bin.basis)}")
                bin.revisions.forEach {(definition,count)->Hint("$definition · $count observed days")}
            }
        }
    }
}
