package dev.riposte.core
import java.io.InputStream
import java.io.ByteArrayOutputStream

/** Android 9 compatible bounded read; also limits decompressed archive entries. */
fun InputStream.readBounded(limit: Int): ByteArray {
    require(limit>0)
    val output=ByteArrayOutputStream();val buffer=ByteArray(8192)
    while(true) {val count=read(buffer,0,minOf(buffer.size,limit-output.size()+1));if(count<0) break
        require(output.size()+count<=limit) {"File exceeds the supported size limit."};output.write(buffer,0,count)
    }
    return output.toByteArray()
}
