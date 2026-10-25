package dev.riposte.imports
import dev.riposte.core.readBounded

import java.io.*
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

object Csv {
    fun read(text: String): List<List<String>> {
        val rows=mutableListOf<List<String>>(); var row=mutableListOf<String>(); val field=StringBuilder(); var quoted=false; var i=0
        val first=text.lineSequence().firstOrNull().orEmpty(); val delimiter=if(first.count { it==';' }>first.count { it==',' }) ';' else if(first.contains('\t')) '\t' else ','
        while(i<text.length) { val c=text[i]
            when { c=='"' -> if(quoted && i+1<text.length && text[i+1]=='"') { field.append('"'); i++ } else quoted=!quoted
                c==delimiter && !quoted -> { row.add(field.toString()); field.clear() }
                (c=='\n' || c=='\r') && !quoted -> { if(c=='\r' && i+1<text.length && text[i+1]=='\n') i++; row.add(field.toString()); field.clear(); if(row.any { it.isNotBlank() }) rows.add(row); row=mutableListOf() }
                else -> field.append(c) }; i++ }
        require(!quoted) { "Unclosed quotation mark in CSV." }
        if(field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows.map { r->r.map { it.trim().removePrefix("\uFEFF") } }
    }
    fun write(rows: List<List<String>>): String = rows.joinToString("\r\n") { row -> row.joinToString(",") { "\""+it.replace("\"","\"\"")+"\"" } }
}

/** Small streaming XLSX reader; does not evaluate formulas or execute external links. */
object Xlsx {
    fun read(input: InputStream): Map<String,List<List<String>>> {
        val files=mutableMapOf<String,ByteArray>(); var total=0
        ZipInputStream(input).use { zip -> while(true) { val e=zip.nextEntry ?: break
            if(!e.isDirectory && (e.name.startsWith("xl/worksheets/") || e.name=="xl/sharedStrings.xml")) {
                val b=zip.readBounded(12_000_001); require(b.size<=12_000_000) { "Spreadsheet sheet is too large." }; total+=b.size; require(total<40_000_000); files[e.name]=b
            }
        } }
        fun xml(b: ByteArray) = DocumentBuilderFactory.newInstance().apply {
            isExpandEntityReferences=false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl",true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities",false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities",false) }
        }.newDocumentBuilder().parse(ByteArrayInputStream(b))
        val strings=files["xl/sharedStrings.xml"]?.let { b-> val nodes=xml(b).getElementsByTagName("si"); (0 until nodes.length).map { nodes.item(it).textContent } }.orEmpty()
        return files.filterKeys { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }.toSortedMap().mapValues { (_,bytes)->
            val doc=xml(bytes); val rows=doc.getElementsByTagName("row")
            (0 until rows.length).map { ri ->
                val cells=(rows.item(ri) as Element).getElementsByTagName("c"); val result=mutableListOf<String>()
                for(ci in 0 until cells.length) { val cell=cells.item(ci) as Element; val ref=cell.getAttribute("r").takeWhile { it.isLetter() }; val column=ref.fold(0) { a,c->a*26+(c.uppercaseChar()-'A'+1) }-1
                    if(column !in 0..500) continue
                    while(result.size<=column) result.add("")
                    val raw=cell.getElementsByTagName("v").item(0)?.textContent.orEmpty()
                    result[column]=when(cell.getAttribute("t")) { "s"->strings.getOrNull(raw.toIntOrNull() ?: -1).orEmpty(); "inlineStr"->cell.getElementsByTagName("is").item(0)?.textContent.orEmpty(); else->raw }
                }; result
            }.filter { it.any(String::isNotBlank) }
        }
    }
}
data class ResultDraft(val row: Int, val a: String, val b: String, val scoreA: Int?, val scoreB: Int?, val outcome: String="auto", val issue: String="", val sourceCell: String="")
object ResultParser {
    fun rows(table: List<List<String>>, a: Int=0,b: Int=1,sa: Int=2,sb: Int=3,skipHeader: Boolean=true): List<ResultDraft> = table.drop(if(skipHeader) 1 else 0).mapIndexed { i,r ->
        val x=r.getOrNull(sa)?.toIntOrNull(); val y=r.getOrNull(sb)?.toIntOrNull()
        ResultDraft(i,r.getOrNull(a).orEmpty(),r.getOrNull(b).orEmpty(),x,y,issue=if(x==null || y==null) "Check scores" else "")
    }
    private fun score(cell: String): Pair<Int?,String> {
        val c=cell.trim().uppercase(); if(c.isEmpty() || c in listOf("-","X","—")) return null to ""
        return Regex("\\d+").find(c)?.value?.toIntOrNull() to when { c.startsWith("V") || c.startsWith("W")->"win"; c.startsWith("D") || c.startsWith("L")->"loss"; else->"" }
    }
    fun matrix(table: List<List<String>>, target: Int=5): List<ResultDraft> {
        if(table.size<2) return emptyList()
        val names=table.drop(1).map { it.firstOrNull().orEmpty() }; val result=mutableListOf<ResultDraft>()
        for(a in names.indices) for(b in a+1 until names.size) {
            val ca=table.getOrNull(a+1)?.getOrNull(b+1).orEmpty(); val cb=table.getOrNull(b+1)?.getOrNull(a+1).orEmpty()
            if(ca.isBlank() && cb.isBlank()) continue
            val (sa,oa)=score(ca); val (sb,ob)=score(cb)
            val winner=when { oa=="win" || ob=="loss"->"A"; ob=="win" || oa=="loss"->"B"; else->"auto" }
            val issue=when { oa.isNotBlank() && oa==ob -> "Conflicting mirrored outcomes"
                sa==null || sb==null -> "Missing score: confirm or enter explicitly"
                winner=="A" && sa<sb || winner=="B" && sb<sa -> "Outcome conflicts with scores"
                sa>target || sb>target -> "Score exceeds target"
                else->"" }
            result.add(ResultDraft(result.size,names[a],names[b],sa,sb,winner,issue,"${a+2}:${b+2} / ${b+2}:${a+2}"))
        }
        return result
    }
}
