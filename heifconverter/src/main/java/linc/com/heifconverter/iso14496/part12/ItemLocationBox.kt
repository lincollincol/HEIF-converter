//
// adhoc patch: org.mp4parser.boxes.iso14496.part12.ItemLocationBox
//
package linc.com.heifconverter.iso14496.part12

import org.mp4parser.support.AbstractFullBox
import org.mp4parser.tools.IsoTypeReader
import org.mp4parser.tools.IsoTypeReaderVariable
import org.mp4parser.tools.IsoTypeWriter
import org.mp4parser.tools.IsoTypeWriterVariable
import java.nio.ByteBuffer
import java.util.*

/*
 * Copyright 2008 CoreMedia AG, Hamburg
 *
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an AS IS BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */ /**
 * <h1>4cc = "{@value #TYPE}"</h1>
 * <pre>
 * aligned(8) class ItemLocationBox extends FullBox('iloc', version, 0) {
 * unsigned int(4) offset_size;
 * unsigned int(4) length_size;
 * unsigned int(4) base_offset_size;
 * if (version == 1)
 * unsigned int(4) index_size;
 * else
 * unsigned int(4) reserved;
 * unsigned int(16) item_count;
 * for (i=0; i&lt;item_count; i++) {
 * unsigned int(16) item_ID;
 * if (version == 1) {
 * unsigned int(12) reserved = 0;
 * unsigned int(4) construction_method;
 * }
 * unsigned int(16) data_reference_index;
 * unsigned int(base_offset_size*8) base_offset;
 * unsigned int(16) extent_count;
 * for (j=0; j&lt;extent_count; j++) {
 * if ((version == 1) &amp;&amp; (index_size &gt; 0)) {
 * unsigned int(index_size*8) extent_index;
 * }
 * unsigned int(offset_size*8) extent_offset;
 * unsigned int(length_size*8) extent_length;
 * }
 * }
 * }
</pre> *
 */
internal class ItemLocationBox :
    AbstractFullBox(TYPE) {
    var offsetSize = 8
    var lengthSize = 8
    var baseOffsetSize = 8
    var indexSize = 0
    private var items: MutableList<Item> =
        LinkedList()

    override fun getContentSize(): Long {
        var size: Long = 8
        for (item in items) {
            size += item.size.toLong()
        }
        return size
    }

    override fun getContent(byteBuffer: ByteBuffer) {
        writeVersionAndFlags(byteBuffer)
        IsoTypeWriter.writeUInt8(byteBuffer, offsetSize shl 4 or lengthSize)
        if (version == 1) {
            IsoTypeWriter.writeUInt8(byteBuffer, baseOffsetSize shl 4 or indexSize)
        } else {
            IsoTypeWriter.writeUInt8(byteBuffer, baseOffsetSize shl 4)
        }
        IsoTypeWriter.writeUInt16(byteBuffer, items.size)
        for (item in items) {
            item.getContent(byteBuffer)
        }
    }

    public override fun _parseDetails(content: ByteBuffer) {
        parseVersionAndFlags(content)
        var tmp = IsoTypeReader.readUInt8(content)
        offsetSize = tmp ushr 4
        lengthSize = tmp and 0xf
        tmp = IsoTypeReader.readUInt8(content)
        baseOffsetSize = tmp ushr 4
        if (version == 1) {
            indexSize = tmp and 0xf
        }
        val itemCount = IsoTypeReader.readUInt16(content)
        for (i in 0 until itemCount) {
            items.add(Item(content))
        }
    }

    fun getItems(): List<Item> {
        return items
    }

    fun setItems(items: MutableList<Item>) {
        this.items = items
    }

    fun createItem(
        itemId: Int,
        constructionMethod: Int,
        dataReferenceIndex: Int,
        baseOffset: Long,
        extents: MutableList<Extent>
    ): Item {
        return Item(
            itemId,
            constructionMethod,
            dataReferenceIndex,
            baseOffset,
            extents
        )
    }

    fun createItem(bb: ByteBuffer?): Item {
        return Item(bb)
    }

    fun createExtent(
        extentOffset: Long,
        extentLength: Long,
        extentIndex: Long
    ): Extent {
        return Extent(
            extentOffset,
            extentLength,
            extentIndex
        )
    }

    fun createExtent(bb: ByteBuffer?): Extent {
        return Extent(bb)
    }

    inner class Item {
        var itemId: Int
        var constructionMethod = 0
        var dataReferenceIndex: Int
        var baseOffset: Long = 0
        var extents: MutableList<Extent>? =
            LinkedList()

        constructor(`in`: ByteBuffer?) {
            itemId = IsoTypeReader.readUInt16(`in`)
            if (version == 1) {
                val tmp = IsoTypeReader.readUInt16(`in`)
                constructionMethod = tmp and 0xf
            }
            dataReferenceIndex = IsoTypeReader.readUInt16(`in`)
            baseOffset = if (baseOffsetSize > 0) {
                IsoTypeReaderVariable.read(`in`, baseOffsetSize)
            } else {
                0
            }
            val extentCount = IsoTypeReader.readUInt16(`in`)
            for (i in 0 until extentCount) {
                extents!!.add(Extent(`in`))
            }
        }

        constructor(
            itemId: Int,
            constructionMethod: Int,
            dataReferenceIndex: Int,
            baseOffset: Long,
            extents: MutableList<Extent>?
        ) {
            this.itemId = itemId
            this.constructionMethod = constructionMethod
            this.dataReferenceIndex = dataReferenceIndex
            this.baseOffset = baseOffset
            this.extents = extents
        }

        val size: Int
            get() {
                var size = 2
                if (version == 1) {
                    size += 2
                }
                size += 2
                size += baseOffsetSize
                size += 2
                for (extent in extents!!) {
                    size += extent.size
                }
                return size
            }

        fun getContent(bb: ByteBuffer?) {
            IsoTypeWriter.writeUInt16(bb, itemId)
            if (version == 1) {
                IsoTypeWriter.writeUInt16(bb, constructionMethod)
            }
            IsoTypeWriter.writeUInt16(bb, dataReferenceIndex)
            if (baseOffsetSize > 0) {
                IsoTypeWriterVariable.write(baseOffset, bb, baseOffsetSize)
            }
            IsoTypeWriter.writeUInt16(bb, extents!!.size)
            for (extent in extents!!) {
                extent.getContent(bb)
            }
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o == null || javaClass != o.javaClass) return false
            val item =
                o as Item
            if (baseOffset != item.baseOffset) return false
            if (constructionMethod != item.constructionMethod) return false
            if (dataReferenceIndex != item.dataReferenceIndex) return false
            if (itemId != item.itemId) return false
            return !if (extents != null) extents != item.extents else item.extents != null
        }

        override fun hashCode(): Int {
            var result = itemId
            result = 31 * result + constructionMethod
            result = 31 * result + dataReferenceIndex
            result = 31 * result + (baseOffset xor (baseOffset ushr 32)).toInt()
            result = 31 * result + if (extents != null) extents.hashCode() else 0
            return result
        }

        override fun toString(): String {
            return "Item{" +
                    "baseOffset=" + baseOffset +
                    ", itemId=" + itemId +
                    ", constructionMethod=" + constructionMethod +
                    ", dataReferenceIndex=" + dataReferenceIndex +
                    ", extents=" + extents +
                    '}'
        }
    }

    inner class Extent {
        var extentOffset: Long = 0
        var extentLength: Long
        var extentIndex: Long = 0

        constructor(
            extentOffset: Long,
            extentLength: Long,
            extentIndex: Long
        ) {
            this.extentOffset = extentOffset
            this.extentLength = extentLength
            this.extentIndex = extentIndex
        }

        constructor(`in`: ByteBuffer?) {
            if (version == 1 && indexSize > 0) {
                extentIndex = IsoTypeReaderVariable.read(`in`, indexSize)
            }
            if (offsetSize > 0) {   // PATCHED
                extentOffset = IsoTypeReaderVariable.read(`in`, offsetSize)
            }
            extentLength = IsoTypeReaderVariable.read(`in`, lengthSize)
        }

        fun getContent(os: ByteBuffer?) {
            if (version == 1 && indexSize > 0) {
                IsoTypeWriterVariable.write(extentIndex, os, indexSize)
            }
            if (offsetSize > 0) {   // PATCHED
                IsoTypeWriterVariable.write(extentOffset, os, offsetSize)
            }
            IsoTypeWriterVariable.write(extentLength, os, lengthSize)
        }

        val size: Int
            get() = (if (indexSize > 0) indexSize else 0) + offsetSize + lengthSize

        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o == null || javaClass != o.javaClass) return false
            val extent =
                o as Extent
            if (extentIndex != extent.extentIndex) return false
            if (extentLength != extent.extentLength) return false
            return extentOffset == extent.extentOffset
        }

        override fun hashCode(): Int {
            var result = (extentOffset xor (extentOffset ushr 32)).toInt()
            result = 31 * result + (extentLength xor (extentLength ushr 32)).toInt()
            result = 31 * result + (extentIndex xor (extentIndex ushr 32)).toInt()
            return result
        }

        override fun toString(): String {
            val sb = StringBuilder()
            sb.append("Extent")
            sb.append("{extentOffset=").append(extentOffset)
            sb.append(", extentLength=").append(extentLength)
            sb.append(", extentIndex=").append(extentIndex)
            sb.append('}')
            return sb.toString()
        }
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("ItemLocationBox")
        sb.append("{offsetSize=").append(offsetSize)
        sb.append(", lengthSize=").append(lengthSize)
        sb.append(", baseOffsetSize=").append(baseOffsetSize)
        sb.append(", indexSize=").append(indexSize)
        sb.append(", items=").append(items)
        sb.append('}')
        return sb.toString()
    }

    companion object {
        const val TYPE = "iloc"
    }
}