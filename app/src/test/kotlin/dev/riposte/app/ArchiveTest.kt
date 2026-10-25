package dev.riposte.app
import dev.riposte.core.*
import dev.riposte.fencing.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.zip.*
class ArchiveTest {
    @Test fun restoredRecordsAndRepeatImportAreStable() {
        val session=Session("session","Test",Stamp(day="2026-10-02"))
        val input=Snapshot(fencers=listOf(Fencer("me","Me",true),Fencer("alice","Alice")),sessions=listOf(session),bouts=listOf(Bout("bout",session.id,"me","alice",5,3,stamp=session.stamp)))
        val result=BackupArchive.unpack(BackupArchive.pack(input) {error("No attachments")}).snapshot
        assertEquals(input,result)
        assertEquals(1,mergeHistory(mergeHistory(Snapshot(),input),input).bouts.size)
    }
    @Test fun rejectsMissingAttachmentsAndTraversal() {
        val archive=ByteArrayOutputStream();ZipOutputStream(archive).use {it.putNextEntry(ZipEntry("../../escape"));it.write(byteArrayOf(1));it.closeEntry()}
        assertThrows(IllegalArgumentException::class.java) {BackupArchive.unpack(archive.toByteArray())}
        assertThrows(IllegalArgumentException::class.java) {Snapshot(fencers=listOf(Fencer("me","Me",true)),bouts=listOf(Bout(sessionId="missing",a="me",b="absent",scoreA=1,scoreB=0,stamp=Stamp()))).validate()}
    }
    @Test fun fullPrivateHistoryReconciles() {
        val file=File("../delivery/life-history.ripostehistory")
        Assume.assumeTrue("Private fixture is not part of source control",file.exists())
        val imported=BackupArchive.unpack(file.readBytes()).snapshot
        assertEquals(2725,imported.bouts.size);assertEquals(12,imported.bouts.count {it.origin.uncertain});assertEquals(36983,imported.observations.size)
        val merged=mergeHistory(Snapshot(),imported);assertEquals(merged,mergeHistory(merged,imported));assertEquals(29,merged.attachments.size)
        assertEquals(713,merged.fencers.size);assertEquals(291,merged.fencers.count {!it.identityKnown});assertEquals(476,merged.bouts.count {it.origin.inferred})
    }
}
