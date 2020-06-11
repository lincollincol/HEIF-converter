package linc.com.heifconverter.iso14496.part12

import org.mp4parser.support.AbstractFullBox
import org.mp4parser.tools.IsoTypeReader
import java.nio.ByteBuffer

internal class PrimaryItemBox : AbstractFullBox(TYPE) {
    var itemId = 0
        private set

    public override fun _parseDetails(content: ByteBuffer) {
        parseVersionAndFlags(content)
        itemId = IsoTypeReader.readUInt16(content)
    }

    public override fun getContentSize(): Long {
        return 10
    }

    public override fun getContent(byteBuffer: ByteBuffer) {
        throw RuntimeException("$TYPE not implemented")
    }

    override fun toString(): String {
        return "PrimaryItemBox[itemId=$itemId]"
    }

    companion object {
        const val TYPE = "pitm"
    }
}