package dev.riposte.imports
import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OcrWord(val text: String,val left: Int,val top: Int,val right: Int,val bottom: Int)
data class OcrResult(val text: String,val words: List<OcrWord>)
object DocumentReader {
    private fun <T> pdf(context: Context,uri: Uri,action: (PdfRenderer)->T): T {
        val file=File.createTempFile("riposte-pdf-",".pdf",context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)!!.use {input->file.outputStream().use {output->input.copyTo(output)}}
            return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use {fd->PdfRenderer(fd).use(action)}
        } finally {file.delete()}
    }
    fun pageCount(context: Context,uri: Uri): Int = pdf(context,uri) {it.pageCount}
    fun bitmap(context: Context,uri: Uri,pdf: Boolean,page: Int=0): Bitmap {
        if(!pdf) return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver,uri)) {decoder,info,_->
            // ImageDecoder applies EXIF orientation. Software bitmaps also work with offline OCR.
            decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
            var sample=1
            while(info.size.width/sample>3000 || info.size.height/sample>3000) sample*=2
            decoder.setTargetSampleSize(sample)
        }
        return pdf(context,uri) { renderer -> renderer.openPage(page).use { p ->
            val scale=2400f/maxOf(p.width,p.height); val bmp=Bitmap.createBitmap((p.width*scale).toInt().coerceAtLeast(1),(p.height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE); p.render(bmp,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); bmp
        } }
    }
    suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.Default) {
        val reader=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try { val text=reader.process(InputImage.fromBitmap(bitmap,0)).await()
            OcrResult(text.text,text.textBlocks.flatMap { it.lines }.flatMap { it.elements }.mapNotNull { e -> e.boundingBox?.let { OcrWord(e.text,it.left,it.top,it.right,it.bottom) } })
        } finally { reader.close() }
    }
    fun grid(words: List<OcrWord>,width: Int,height: Int,rows: Int,columns: Int): List<List<String>> = (0 until rows).map { r -> (0 until columns).map { c ->
        words.filter { (it.top+it.bottom)/2 in (r*height/rows) until ((r+1)*height/rows) && (it.left+it.right)/2 in (c*width/columns) until ((c+1)*width/columns) }.sortedBy { it.left }.joinToString(" ") { it.text }
    } }
}
