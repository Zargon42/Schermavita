package dev.riposte.app
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Only the nine line icons used by the app; no large icon-font dependency. */
object Glyph {
    private fun icon(name:String,draw:PathBuilder.()->Unit)=ImageVector.Builder(name,24.dp,24.dp,24f,24f).apply {
        path(fill=null,stroke=SolidColor(Color.Black),strokeLineWidth=1.8f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round,pathBuilder=draw)
    }.build()
    val Close=icon("Close") {moveTo(6f,6f);lineTo(18f,18f);moveTo(6f,18f);lineTo(18f,6f)}
    val ArrowBack=icon("Back") {moveTo(20f,12f);lineTo(4f,12f);lineTo(10f,6f);moveTo(4f,12f);lineTo(10f,18f)}
    val WbSunny=icon("Today") {moveTo(16f,12f);curveTo(16f,17.3f,8f,17.3f,8f,12f);curveTo(8f,6.7f,16f,6.7f,16f,12f);moveTo(12f,2f);lineTo(12f,4f);moveTo(12f,20f);lineTo(12f,22f);moveTo(2f,12f);lineTo(4f,12f);moveTo(20f,12f);lineTo(22f,12f);moveTo(5f,5f);lineTo(6.5f,6.5f);moveTo(17.5f,17.5f);lineTo(19f,19f);moveTo(5f,19f);lineTo(6.5f,17.5f);moveTo(17.5f,6.5f);lineTo(19f,5f)}
    val Sports=icon("Fencing") {moveTo(5f,21f);lineTo(20f,3f);moveTo(4f,15f);lineTo(11f,21f);moveTo(7f,13f);lineTo(13f,18f)}
    val History=icon("History") {moveTo(4f,8f);curveTo(8f,-1f,23f,4f,21f,14f);curveTo(19f,23f,6f,24f,3f,15f);moveTo(3f,3f);lineTo(3f,9f);lineTo(9f,9f);moveTo(12f,7f);lineTo(12f,13f);lineTo(16f,15f)}
    val Insights=icon("Insights") {moveTo(3f,3f);lineTo(3f,21f);lineTo(22f,21f);moveTo(6f,16f);lineTo(11f,10f);lineTo(15f,13f);lineTo(21f,5f)}
    val FileDownload=icon("Import") {moveTo(12f,2f);lineTo(12f,15f);moveTo(7f,10f);lineTo(12f,15f);lineTo(17f,10f);moveTo(4f,16f);lineTo(4f,21f);lineTo(20f,21f);lineTo(20f,16f)}
    val Settings=icon("Settings") {moveTo(4f,5f);lineTo(20f,5f);moveTo(4f,12f);lineTo(20f,12f);moveTo(4f,19f);lineTo(20f,19f);moveTo(8f,2f);lineTo(8f,8f);moveTo(16f,9f);lineTo(16f,15f);moveTo(10f,16f);lineTo(10f,22f)}
}
