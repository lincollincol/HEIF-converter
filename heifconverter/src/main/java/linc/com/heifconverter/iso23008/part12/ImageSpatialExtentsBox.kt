package linc.com.heifconverter.iso23008.part12

import org.mp4parser.support.AbstractFullBox
import org.mp4parser.tools.IsoTypeReader
import java.nio.ByteBuffer

internal class ImageSpatialExtentsBox :
    AbstractFullBox(TYPE) {
    var displayWidth: Long = 0
    var displayHeight: Long = 0
    public override fun _parseDetails(content: ByteBuffer) {
        parseVersionAndFlags(content)
        displayWidth = IsoTypeReader.readUInt32(content)
        displayHeight = IsoTypeReader.readUInt32(content)
    }

    public override fun getContentSize(): Long {
        return 16
    }

    public override fun getContent(byteBuffer: ByteBuffer) {
        throw RuntimeException("$TYPE not implemented")
    }

    override fun toString(): String {
        return "ImageSpatialExtentsBox[" + displayWidth + "x" + displayHeight + "]"
    }

    companion object {
        const val TYPE = "ispe"
    }
}