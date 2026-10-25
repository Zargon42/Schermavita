package dev.riposte.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import dev.riposte.core.*
import dev.riposte.imports.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString

data class LoadedImport(val name: String,val raw: ByteArray,val bitmap: Bitmap?=null,val mime: String="application/octet-stream",
    val pdf: Boolean=false,val pages: Int=1,val table: String="",
    val sheets: Map<String,List<List<String>>> = emptyMap(),val history: BackupArchive.Contents?=null)

/** Stage a source completely before replacing the user's current import draft. */
suspend fun readImportSource(context: Context,uri: Uri,nameOverride: String?=null): LoadedImport = withContext(Dispatchers.IO) {
    val mime=context.contentResolver.getType(uri).orEmpty()
    val name=nameOverride ?: runCatching {context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {
        if(it.moveToFirst()) it.getString(0)?.takeIf(String::isNotBlank) else null
    }}.getOrNull() ?: if(mime.startsWith("image/")) "Selected photo" else "Selected file"
    val knownSize=runCatching {context.contentResolver.query(uri,arrayOf(OpenableColumns.SIZE),null,null,null)?.use {
        if(it.moveToFirst() && !it.isNull(0)) it.getLong(0).takeIf {size->size>=0} else null
    }}.getOrNull()
    require(knownSize==null || knownSize<=100_000_000L) {"Choose a file under 100 MB."}
    val raw=requireNotNull(context.contentResolver.openInputStream(uri)) {"Cannot open this file. Choose it again from storage."}.use {it.readBounded(100_000_001)}
    require(raw.size<=100_000_000) {"Choose a file under 100 MB."}
    val ext=name.substringAfterLast('.',"").lowercase()
    val loaded=when {
        ext in listOf("zip","ripostehistory") -> LoadedImport(name,raw,history=BackupArchive.unpack(raw))
        ext=="json" -> LoadedImport(name,raw,history=BackupArchive.Contents(codec.decodeFromString<Snapshot>(raw.decodeToString()).also {it.validate()},emptyMap()))
        ext=="xlsx" -> {val sheets=Xlsx.read(raw.inputStream());LoadedImport(name,raw,sheets=sheets,table=sheets.values.firstOrNull()?.let(Csv::write).orEmpty())}
        ext in listOf("csv","tsv","txt") -> LoadedImport(name,raw,table=raw.decodeToString())
        else -> {
            val pdf=ext=="pdf" || mime=="application/pdf" || raw.take(5).toByteArray().decodeToString()=="%PDF-"
            val image=try {DocumentReader.bitmap(context,uri,pdf)} catch(e: Exception) {
                throw IllegalArgumentException("Cannot read this image or document. Choose a supported photo or PDF.",e)
            }
            val detected=if(pdf) "application/pdf" else BitmapFactory.Options().apply {inJustDecodeBounds=true;BitmapFactory.decodeByteArray(raw,0,raw.size,this)}.outMimeType
            LoadedImport(name,raw,bitmap=image,mime=detected ?: mime.ifBlank {"application/octet-stream"},pdf=pdf,pages=if(pdf) DocumentReader.pageCount(context,uri) else 1)
        }
    }
    if(loaded.mime=="application/octet-stream" && mime.isNotBlank()) loaded.copy(mime=mime) else loaded
}
