package dev.riposte.app

import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenTimeDeviceTest {
    @Test fun permissionSyncAndRevocationPreserveExistingRecords() = runBlocking {
        assumeTrue("Permission changes are restricted to the disposable emulator",Build.HARDWARE=="ranchu")
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val repo=(context.applicationContext as TrackerApp).repository
        repo.ready.first {it}
        val before=repo.state.value
        fun access(mode: String) {
            val descriptor=instrumentation.uiAutomation.executeShellCommand("appops set ${context.packageName} GET_USAGE_STATS $mode")
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use {it.readText()}
        }
        try {
            repo.change {it.copy(settings=it.settings.copy(screenTimeEnabled=true))}
            access("deny")
            assertFalse(usageAccess(context))
            syncScreenTime(repo)
            assertEquals(before.observations,repo.state.value.observations)
            access("allow")
            assertTrue(usageAccess(context))
            syncScreenTime(repo)
            assertTrue(repo.state.value.settings.lastScreenTimeSync.isNotEmpty())
            assertEquals(1,repo.state.value.metrics.count {it.id=="screen_time"})
            syncScreenTime(repo)
            assertEquals(repo.state.value.observations.size,repo.state.value.observations.distinctBy {it.id}.size)
            assertEquals(before.bouts,repo.state.value.bouts)
            assertEquals(before.fencers,repo.state.value.fencers)
            assertTrue(repo.state.value.observations.containsAll(before.observations.filter {it.origin.source!="Android Usage Access"}))
            val readings=repo.state.value.observations
            access("deny")
            syncScreenTime(repo)
            assertEquals(readings,repo.state.value.observations)
        } finally {
            access("deny")
            repo.change {it.copy(settings=before.settings)}
        }
    }
}
