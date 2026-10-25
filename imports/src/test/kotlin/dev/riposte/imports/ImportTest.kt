package dev.riposte.imports
import org.junit.Assert.*
import org.junit.Test
class ImportTest {
    @Test fun quotedNewlinesAndEscapes() { assertEquals("A, B",Csv.read("name,score\n\"A, B\",5")[1][0]); assertEquals("two\nlines",Csv.read("\"two\nlines\",3")[0][0]) }
    @Test fun pouleMirrorsProduceOneBout() {
        val r=ResultParser.matrix(listOf(listOf("","A","B"),listOf("A","X","V5"),listOf("B","D3","X")))
        assertEquals(1,r.size); assertEquals(5,r[0].scoreA); assertEquals(3,r[0].scoreB); assertEquals("",r[0].issue)
    }
    @Test fun blankIsNotZeroAndConflictsFlagged() {
        assertEquals(0,ResultParser.matrix(listOf(listOf("","A","B"),listOf("A","X",""),listOf("B","","X"))).size)
        assertTrue(ResultParser.matrix(listOf(listOf("","A","B"),listOf("A","X","V5"),listOf("B","V5","X")))[0].issue.isNotBlank())
    }
}
