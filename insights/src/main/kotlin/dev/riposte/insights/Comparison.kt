package dev.riposte.insights
import dev.riposte.life.*
import java.time.LocalDate
import kotlin.math.sqrt
data class Comparison(val pairs: List<Pair<Double,Double>>, val correlation: Double?)
fun regressionLine(points: List<Pair<Double,Double>>): List<Pair<Double,Double>> {
    if(points.size < 2) return emptyList()
    val meanX=points.map { it.first }.average()
    val meanY=points.map { it.second }.average()
    val denominator=points.sumOf { (it.first-meanX)*(it.first-meanX) }
    if(denominator==0.0) return emptyList()
    val slope=points.sumOf { (it.first-meanX)*(it.second-meanY) }/denominator
    val intercept=meanY-slope*meanX
    val minX=points.minOf { it.first }; val maxX=points.maxOf { it.first }
    return listOf(minX to intercept+slope*minX,maxX to intercept+slope*maxX)
}
fun compare(x: List<DailyValue>, y: List<DailyValue>, precedingDay: Boolean): Comparison {
    val ys=y.associateBy { Triple(it.day,it.basis,it.scale) }
    // Callers select one scale per metric; date bases must match.
    val pairs=x.mapNotNull { a ->
        val target=if(precedingDay) LocalDate.parse(a.day).plusDays(1).toString() else a.day
        y.firstOrNull { it.day==target && it.basis==a.basis }?.let { a.value to it.value }
    }
    if(pairs.size<3) return Comparison(pairs,null)
    val ax=pairs.map { it.first }.average(); val ay=pairs.map { it.second }.average()
    val xx=pairs.sumOf { (it.first-ax)*(it.first-ax) }; val yy=pairs.sumOf { (it.second-ay)*(it.second-ay) }
    return Comparison(pairs,if(xx==0.0 || yy==0.0) null else pairs.sumOf { (it.first-ax)*(it.second-ay) }/sqrt(xx*yy))
}
