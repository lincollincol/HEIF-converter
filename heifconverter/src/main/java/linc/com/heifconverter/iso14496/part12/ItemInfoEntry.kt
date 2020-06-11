package linc.com.heifconverter.iso14496.part12

import org.mp4parser.support.AbstractFullBox
import org.mp4parser.tools.IsoTypeReader
import java.nio.ByteBuffer

internal class ItemInfoEntry : AbstractFullBox(TYPE) {
    private var contentSize: Long = 0
    private var itemId = 0
    private var itemProtectionIndex = 0
    private var itemName: String? = null
    private var contentType: String? = null
    public override fun _parseDetails(content: ByteBuffer) {
        contentSize = content.limit().toLong()
        parseVersionAndFlags(content)
        itemId = IsoTypeReader.readUInt16(content)
        itemProtectionIndex = IsoTypeReader.readUInt16(content)
        itemName = IsoTypeReader.readString(content, 4)
        contentType = IsoTypeReader.readString(content)
    }

    public override fun getContentSize(): Long {
        return contentSize
    }

    public override fun getContent(byteBuffer: ByteBuffer) {
        throw RuntimeException("$TYPE not implemented")
    }

    override fun toString(): String {
        return ("ItemInfoEntry[itemId=" + itemId + ";itemProtectionIndex=" + itemProtectionIndex
                + ";itemName=" + itemName + ";contentType=" + contentType + "]")
    }

    companion object {
        const val TYPE = "infe"
    }
}