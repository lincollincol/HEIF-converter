package linc.com.heifconverter.iso14496.part12

import org.mp4parser.BoxParser
import org.mp4parser.support.AbstractContainerBox
import org.mp4parser.tools.IsoTypeReader
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel

internal class ItemInfoBox : AbstractContainerBox(TYPE) {
    private var version = 0
    private var flags = 0

    @Throws(IOException::class)
    override fun parse(
        dataSource: ReadableByteChannel,
        header: ByteBuffer,
        contentSize: Long,
        boxParser: BoxParser
    ) {
        var buffer = ByteBuffer.allocate(4)
        dataSource.read(buffer)
        buffer.rewind()
        version = IsoTypeReader.readUInt8(buffer)
        flags = IsoTypeReader.readUInt24(buffer)
        val entryCountLength = if (version == 0) 2 else 4
        buffer = ByteBuffer.allocate(entryCountLength)
        dataSource.read(buffer)
        buffer.rewind()
        initContainer(dataSource, contentSize - 4 - entryCountLength, boxParser)
        for (entry in getBoxes(ItemInfoEntry::class.java)) {
            entry.parseDetails()
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
        const val TYPE = "iinf"
    }
}