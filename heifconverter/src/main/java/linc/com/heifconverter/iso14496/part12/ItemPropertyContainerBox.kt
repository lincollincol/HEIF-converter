package linc.com.heifconverter.iso14496.part12

import org.mp4parser.BoxParser
import org.mp4parser.support.AbstractBox
import org.mp4parser.support.AbstractContainerBox
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel

internal class ItemPropertyContainerBox :
    AbstractContainerBox(TYPE) {
    @Throws(IOException::class)
    override fun parse(
        dataSource: ReadableByteChannel,
        header: ByteBuffer,
        contentSize: Long,
        boxParser: BoxParser
    ) {
        initContainer(dataSource, contentSize, boxParser)
        for (box in this.getBoxes(AbstractBox::class.java)) {
            box.parseDetails()
        }
    }

    @Throws(IOException::class)
    override fun getBox(writableByteChannel: WritableByteChannel) {
        throw RuntimeException("$TYPE not implemented")
    }

    override fun getSize(): Long {
        val s = containerSize
        val t: Long = 6
        return s + t + if (largeBox || s + t + 8 >= 1L shl 32) 16 else 8
    }

    companion object {
        const val TYPE = "ipco"
    }
}