package dev.riposte.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.riposte.core.*
import dev.riposte.fencing.*
import dev.riposte.life.*
import dev.riposte.insights.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class InsightsDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val repo get()=(compose.activity.application as TrackerApp).repository
    @Before fun resetSyntheticData() {
        compose.waitUntil(30000) {repo.ready.value}
        runBlocking {repo.change {Snapshot()};repo.ensureDay();repo.selectTab(0)}
        compose.waitForIdle()
    }
    @Test fun performanceChartsMatchScreenTimeAndShowOpponentBaselines() {
        val session=Session("session","Club",Stamp(day="2026-10-01"))
        val bouts=(1..7).map {i->Bout("b$i",session.id,"me","alice",5,3,target=5,stamp=Stamp(day="2026-10-0$i"),detailsA=ParticipantDetails(rating=4.0))}
        val metric=chartableMetrics(emptyList()).single()
        val observations=(1..7).map {i->Observation("screen$i",metric.id,Stamp(day="2026-10-0$i"),i*10.0,scope="aggregate",unit="min",scale=metric.scale)}
        runBlocking {repo.change {it.copy(fencers=it.fencers+Fencer("alice","Alice"),sessions=listOf(session),bouts=bouts,metrics=it.metrics+metric,observations=observations)}}
        compose.onNodeWithText("Insights",useUnmergedTree=true).performClick()
        fun select(label: String,current: String) {
            compose.onNodeWithText("Performance: $current").performScrollTo().performClick()
            compose.onNode(hasText(label,substring=false) and hasClickAction()).performClick()
        }
        select("Points for / against","Touch share")
        compose.onNodeWithText("● Points against").assertExists()
        select("Wins / losses","Points for / against")
        compose.onNodeWithText("● Losses").assertExists()
        select("Win rate","Wins / losses")
        select("Self-evaluation","Win rate")
        select("Opponent-adjusted touch share","Self-evaluation")
        compose.waitUntil(10000) {compose.onAllNodesWithText("5 bouts excluded",substring=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onAllNodesWithText("Inspect first point").onFirst().performScrollTo().performClick()
        compose.onNodeWithText("2026-10-06 · Opponent-adjusted touch share:",substring=true).assertExists()
        compose.onNodeWithText("Life metric: Mood").performScrollTo().performClick()
        compose.onNode(hasText("Screen time",substring=false) and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("2 paired days",substring=true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Life metric on the previous day").performScrollTo()
        compose.onAllNodes(isToggleable()).filter(hasAnySibling(hasText("Life metric on the previous day"))).onFirst().performClick()
        compose.onNodeWithText("Opponent baselines").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Actual",substring=true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Life",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("Trend: Screen time").assertExists()
        compose.onNodeWithText("7 observed days",substring=true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Compare with: Stress").performScrollTo().performClick()
        compose.onNode(hasText("Screen time",substring=false) and hasClickAction()).performScrollTo().performClick()
        // Previous-day mode remains selected: seven consecutive observations make six pairs.
        compose.onNodeWithText("6 paired days",substring=true).performScrollTo().assertIsDisplayed()
        assertFalse(repo.state.value.settings.screenTimeEnabled)
    }

    private fun storedPhoto(name: String="stored-sheet"): Uri {
        val context=compose.activity
        val file=File(context.cacheDir,"camera/$name").apply {parentFile!!.mkdirs()}
        val bitmap=Bitmap.createBitmap(1200,500,Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {drawColor(Color.WHITE);drawText("Me Alice 5 3",50f,150f,Paint().apply {color=Color.BLACK;textSize=70f})}
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        return FileProvider.getUriForFile(context,"${context.packageName}.files",file)
    }

    @Test fun storedPhotoImportReviewsSavesDetectsDuplicatesAndUndoes() {
        val photo=storedPhoto()
        var cancel=false
        val registry=object: ActivityResultRegistry() {
            override fun <I,O> onLaunch(requestCode: Int,contract: ActivityResultContract<I,O>,input: I,options: ActivityOptionsCompat?) {
                dispatchResult(requestCode,if(cancel) null else photo)
            }
        }
        val owner=object: ActivityResultRegistryOwner {override val activityResultRegistry=registry}
        compose.activity.runOnUiThread {compose.activity.setContent {
            val state by repo.state.collectAsState()
            val scope=rememberCoroutineScope()
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                RiposteTheme {Box(Modifier.safeDrawingPadding()) {ImportScreen(repo,state) {task->scope.launch {task()}}}}
            }
        }}
        compose.onNodeWithText("Choose photo").performClick()
        compose.waitUntil(15000) {compose.onAllNodesWithContentDescription("Source page").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("stored-sheet").assertExists()
        cancel=true
        compose.onNodeWithText("Choose photo").performScrollTo().performClick()
        compose.onNodeWithText("stored-sheet").assertExists()
        cancel=false
        compose.onNodeWithText("Recognise text offline").performScrollTo().performClick()
        compose.waitUntil(30000) {compose.onAllNodesWithText("Text recognised.",substring=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Editable table · CSV or tab-separated").performScrollTo().performTextReplacement("fencer_a,fencer_b,score_a,score_b\nMe,Alice,5,3")
        compose.runOnIdle {compose.activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            .hideSoftInputFromWindow(compose.activity.window.decorView.windowToken,0)}
        compose.onNodeWithText("Preview bouts").performScrollTo().performClick()
        compose.onNodeWithText("Confirm all rows without warnings").performScrollTo().performClick()
        compose.onNodeWithText("Import reviewed results").performScrollTo().performClick()
        compose.waitUntil(10000) {repo.state.value.bouts.size==1}
        assertEquals(1,repo.state.value.attachments.size)
        assertEquals("image/png",repo.state.value.attachments.single().mime)
        assertTrue(repo.sourceFile(repo.state.value.attachments.single().id).exists())
        compose.onNodeWithText("Choose photo").performScrollTo().performClick()
        compose.waitUntil(15000) {compose.onAllNodesWithText("This file was imported before.").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("This file was imported before.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Undo this import").performScrollTo().performClick()
        compose.onNodeWithText("Undo import",substring=false).performClick()
        compose.waitUntil(10000) {repo.state.value.bouts.none {!it.deleted} && repo.state.value.batches.isEmpty()}
        assertTrue(repo.state.value.fencers.any {it.name=="Alice"})
    }

    @Test fun storedImagesApplyExifOrientationAndRejectUnreadableSources() = runBlocking {
        val context=compose.activity
        val file=File(context.cacheDir,"camera/oriented.jpg").apply {parentFile!!.mkdirs()}
        val bitmap=Bitmap.createBitmap(120,80,Bitmap.Config.ARGB_8888)
        val jpeg=java.io.ByteArrayOutputStream().also {bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)}.toByteArray();bitmap.recycle()
        // JPEG APP1 containing a little-endian TIFF orientation tag (6 = rotate 90 degrees).
        val exif=byteArrayOf(0xff.toByte(),0xe1.toByte(),0,34,69,120,105,102,0,0,73,73,42,0,8,0,0,0,1,0,18,1,3,0,1,0,0,0,6,0,0,0,0,0,0,0)
        file.writeBytes(jpeg.take(2).toByteArray()+exif+jpeg.drop(2).toByteArray())
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file)
        val loaded=readImportSource(context,uri)
        assertEquals(80,loaded.bitmap!!.width);assertEquals(120,loaded.bitmap!!.height)
        file.writeText("not a photo")
        try {readImportSource(context,uri);fail("Unreadable source accepted")} catch(e: IllegalArgumentException) {assertTrue(e.message!!.contains("Cannot read"))}
        java.io.RandomAccessFile(file,"rw").use {it.setLength(100_000_001L)}
        try {
            try {readImportSource(context,uri);fail("Oversized source accepted")} catch(e: IllegalArgumentException) {assertTrue(e.message!!.contains("under 100 MB"))}
        } finally {file.delete()}
    }
}
