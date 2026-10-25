package dev.riposte.app
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HistoryDeviceTest {
    @Test fun completeHistoryImportReloadAndRepeat() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.filesDir,"test-history.ripostehistory")
        Assume.assumeTrue("Full private history fixture is supplied separately",file.exists())
        val repo=(context.applicationContext as TrackerApp).repository;repo.ready.first {it}
        val contents=BackupArchive.unpack(file.readBytes())
        assertEquals(2725,contents.snapshot.bouts.size)
        assertEquals(12,contents.snapshot.bouts.count {it.origin.uncertain})
        contents.sources.forEach {(id,bytes)->val dest=repo.sourceFile(id);dest.parentFile!!.mkdirs();dest.writeBytes(bytes)}
        repo.change {mergeHistory(it,contents.snapshot)}
        val count=repo.state.value.observations.size
        repo.change {mergeHistory(it,contents.snapshot)}
        assertEquals(count,repo.state.value.observations.size)
        assertEquals(2725,repo.state.value.bouts.count {it.origin.source=="Historical fencing_bouts"})
        val reopened=AppRepository(context);reopened.load()
        assertEquals(count,reopened.state.value.observations.size)
        assertEquals(29,reopened.state.value.attachments.size)
    }
}
