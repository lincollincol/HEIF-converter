package linc.com.heifconverter.iso14496.part12

import org.mp4parser.support.AbstractFullBox
import org.mp4parser.tools.IsoTypeReader
import java.nio.ByteBuffer
import java.util.*

internal class ItemPropertyAssociation :
    AbstractFullBox(TYPE) {
    private var contentSize: Long = 0
    internal val items: MutableList<Item> =
        ArrayList()

    class Assoc {
        var essential = 0
        var propertyIndex = 0
    }

    class Item {
        var item_ID: Long = 0
        var associations: MutableList<Assoc> = ArrayList()
    }

    fun getItems(): List<Item> {
        return items
    }

    public override fun _parseDetails(content: ByteBuffer) {
        contentSize = content.limit().toLong()
        parseVersionAndFlags(content)
        val entryCount = IsoTypeReader.readUInt32(content)
        for (i in 0 until entryCount) {
            val item =
                Item()
            if (version < 1) {
                item.item_ID = IsoTypeReader.readUInt16(content).toLong()
            } else {
                item.item_ID = IsoTypeReader.readUInt32(content)
            }
            val associationCount = IsoTypeReader.readUInt8(content)
            for (j in 0 until associationCount) {
                val assoc = Assoc()
                var value: Int
                var indexLength: Int
                if (flags and 1 == 1) {
                    value = IsoTypeReader.readUInt16(content)
                    indexLength = 15
                } else {
                    value = IsoTypeReader.readUInt8(content)
                    indexLength = 7
                }
                assoc.essential = value shr indexLength
                assoc.propertyIndex = value and (1 shl indexLength) - 1
                item.associations.add(assoc)
            }
            items.add(item)
        }
    }

    public override fun getContentSize(): Long {
        return contentSize
    }

    public override fun getContent(byteBuffer: ByteBuffer) {
        throw RuntimeException("$TYPE not implemented")
    }

    override fun toString(): String {
        return "ItemPropertyAssociation"
    }

    companion object {
        const val TYPE = "ipma"
    }
}