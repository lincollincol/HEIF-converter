package linc.com.heifconverter

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.media.*
import android.os.SystemClock
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.Type
import android.util.Log
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.*
import linc.com.heifconverter.iso14496.part12.*
import linc.com.heifconverter.iso23008.part12.ImageSpatialExtentsBox
import org.mp4parser.Box
import org.mp4parser.IsoFile
import org.mp4parser.boxes.iso14496.part12.FileTypeBox
import org.mp4parser.boxes.iso14496.part15.HevcConfigurationBox
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.util.*
import kotlin.coroutines.CoroutineContext

/**
 * HEIF(High Efficiency Image Format) reader
 *
 * Create Bitmap object from HEIF file, byte-array, stream, etc.
 */
internal object HeifReader {
    private const val TAG = "HeifReader"

    /**
     * input data size limitation for safety.
     */
    private const val LIMIT_FILESIZE = 20 * 1024 * 1024 // 20[MB]
        .toLong()
    private var mRenderScript: RenderScript? = null
    private var mCacheDir: File? = null
    private var mDecoderName: String? = null
    private var mDecoderSupportedSize: Size? = null

    /**
     * Initialize HeifReader module.
     *
     * @param context Context.
     */
    fun initialize(context: Context) {
        mRenderScript = RenderScript.create(context)
        mCacheDir = context.cacheDir

        // find best HEVC decoder
        mDecoderName = null
        mDecoderSupportedSize = Size(0, 0)
        val numCodecs = MediaCodecList.getCodecCount()
        for (i in 0 until numCodecs) {
            val codecInfo = MediaCodecList.getCodecInfoAt(i)
            if (codecInfo.isEncoder) {
                continue
            }
            for (type in codecInfo.supportedTypes) {
                if (type.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true)) {
                    val cap =
                        codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_HEVC)
                    val vcap = cap.videoCapabilities
                    val supportedSize = Size(
                        vcap.supportedWidths.upper,
                        vcap.supportedHeights.upper
                    )
                    Log.d(
                        TAG, "HEVC decoder=\"" + codecInfo.name + "\""
                                + " supported-size=" + supportedSize
                                + " color-formats=" + Arrays.toString(cap.colorFormats)
                    )
                    if (mDecoderSupportedSize!!.width * mDecoderSupportedSize!!.height < supportedSize.width * supportedSize.height) {
                        mDecoderName = codecInfo.name
                        mDecoderSupportedSize = supportedSize
                    }
                }
            }
        }
        if (mDecoderName == null) {
            throw RuntimeException("no HEVC decoding support")
        }
        Log.i(TAG,"HEVC decoder=\"$mDecoderName\" supported-size=$mDecoderSupportedSize")
    }

    /**
     * Decode a bitmap from the specified byte array.
     *
     * @param data byte array of compressed image data.
     * @return The decoded bitmap, or null if the image could not be decoded.
     */
    suspend fun decodeByteArray(data: ByteArray): Bitmap? {
        assertPrecondition()
        return try {
            val bais = ByteArrayInputStream(data)
            val isoFile = IsoFile(Channels.newChannel(bais))
            val info = parseHeif(isoFile)
            val bitstream = extractBitstream(data, info)
            try {
                renderHevcImageWithFormat(bitstream, info, ImageFormat.YV12)
            } catch (ex: FormatFallbackException) {
                Log.w(TAG, "rendering YV12 format failure; fallback to RGB565")
                try {
                    bitstream.rewind()

                    renderHevcImageWithFormat(
                        bitstream,
                        info,
                        ImageFormat.RGB_565
                    )
                } catch (ex2: FormatFallbackException) {
                    Log.e(TAG, "rendering RGB565 format failure", ex2)
                    null
                }
            }
        } catch (ex: IOException) {
            Log.e(TAG, "decodeByteArray failure", ex)
            null
        }
    }

    /**
     * Decode a file path into a bitmap.
     *
     * @param pathName complete path name for the file to be decoded.
     * @return The decoded bitmap, or null if the image could not be decoded.
     */
    suspend fun decodeFile(pathName: String?): Bitmap? {
        assertPrecondition()
        try {
            val file = File(pathName)
            val fileSize = file.length()
            if (LIMIT_FILESIZE < fileSize) {
                Log.e(TAG, "file size exceeds limit($LIMIT_FILESIZE)")
                return null
            }
            val data = ByteArray(fileSize.toInt())
            FileInputStream(file).use { fis ->
                fis.read(data)
                return decodeByteArray(data)
            }
        } catch (ex: IOException) {
            Log.e(TAG, "decodeFile failure", ex)
            return null
        }
    }

    /**
     * Decode a raw resource into a bitmap.
     *
     * @param res The resources object containing the image data.
     * @param id The resource id of the image data.
     * @return The decoded bitmap, or null if the image could not be decoded.
     */
    suspend fun decodeResource(res: Resources, id: Int): Bitmap? {
        assertPrecondition()
        return try {
            val length = res.openRawResourceFd(id).length.toInt()
            val data = ByteArray(length)
            res.openRawResource(id).read(data)
            decodeByteArray(data)
        } catch (ex: IOException) {
            Log.e(TAG, "decodeResource failure", ex)
            null
        }
    }

    /**
     * Decode an input stream into a bitmap.
     *
     * This method save input stream to temporary file on cache directory, because HEIF data
     * structure requires multi-pass parsing.
     *
     * @param is The input stream that holds the raw data to be decoded into a bitmap.
     * @return The decoded bitmap, or null if the image could not be decoded.
     */
    suspend fun decodeStream(`is`: InputStream): Bitmap? {
        assertPrecondition()
        return try {
            // write stream to temporary file
            val beginTime = SystemClock.elapsedRealtimeNanos()
            val heifFile =
                File.createTempFile("heifreader", "heif", mCacheDir)
                FileOutputStream(heifFile).use { fos ->
                val buf = ByteArray(4096)
                var totalLength = 0
                var len: Int
                while (`is`.read(buf).also { len = it } > 0) {
                    fos.write(buf, 0, len)
                    totalLength += len
                    if (LIMIT_FILESIZE < totalLength) {
                        Log.e(TAG, "data size exceeds limit($LIMIT_FILESIZE)")
                        return null
                    }
                }
            }
            val endTime = SystemClock.elapsedRealtimeNanos()
            Log.i(TAG, "HEIC caching elapsed=" + (endTime - beginTime) / 1000000f + "[msec]")
            decodeFile(heifFile.absolutePath)
        } catch (ex: IOException) {
            Log.e(TAG, "decodeStream failure", ex)
            null
        }
    }

    /**
     * Decode url into a bitmap.
     *
     * @param heicImageUrl The url to heic image data to be decoded into a bitmap.
     * @return The decoded bitmap, or null if the image could not be decoded.
     */
    suspend fun decodeUrl(heicImageUrl: String): Bitmap? {
        val url = URL(heicImageUrl)
        val huc: HttpURLConnection = url.openConnection() as HttpURLConnection
        val responseCode: Int = huc.responseCode
        if(HttpURLConnection.HTTP_NOT_FOUND == responseCode)
            throw FileNotFoundException("Invalid url!")
        return decodeStream(URL(heicImageUrl).openStream())
    }

    private fun assertPrecondition() {
        checkNotNull(mRenderScript) { "HeifReader is not initialized." }
    }

    @Throws(IOException::class)
    private fun parseHeif(isoFile: IsoFile): ImageInfo {
        // validate brand compatibility ('ftyp' box)
        val ftypBoxes = isoFile.getBoxes(
            FileTypeBox::class.java
        )
        if (ftypBoxes.size != 1) {
            throw IOException("FileTypeBox('ftyp') shall be unique")
        }
        val ftypBox = ftypBoxes[0]
        Log.d(TAG, "HEIC ftyp=$ftypBox")
        if (!("mif1" == ftypBox.majorBrand || "heic" == ftypBox.majorBrand || !ftypBox.compatibleBrands
                .contains("heic"))
        ) {
            throw IOException("unsupported FileTypeBox('ftyp') brands")
        }

        // get primary item_ID
        val pitmBoxes = isoFile.getBoxes(
            PrimaryItemBox::class.java, true
        )
        if (pitmBoxes.isEmpty()) {
            throw IOException("PrimaryItemBox('pitm') not found")
        }
        val pitmBox = pitmBoxes[0]
        pitmBox.parseDetails()
        Log.d(TAG, "HEIC primary item_ID=" + pitmBox.itemId)

        // get associative item properties
        val iprpBox =
            isoFile.getBoxes(ItemPropertiesBox::class.java, true)[0]
        val ipmaBox = iprpBox.getBoxes(
            ItemPropertyAssociation::class.java
        )[0]
        val ipcoBox = iprpBox.getBoxes(
            ItemPropertyContainerBox::class.java
        )[0]
        val primaryPropBoxes: MutableList<Box> =
            ArrayList()
        for (item in ipmaBox.items) {
            if (item.item_ID == pitmBox.itemId.toLong()) {
                for (assoc in item.associations) {
                    primaryPropBoxes.add(ipcoBox.boxes[assoc.propertyIndex - 1])
                }
            }
        }

        // get image size
        val info = ImageInfo()
        val ispeBox = findBox(
            primaryPropBoxes,
            ImageSpatialExtentsBox::class.java
        )
            ?: throw IOException("ImageSpatialExtentsBox('ispe') not found")
        info.size = Size(ispeBox.displayWidth.toInt(), ispeBox.displayHeight.toInt())
        Log.i(TAG, "HEIC image size=" + ispeBox.displayWidth + "x" + ispeBox.displayHeight)

        // get HEVC decoder configuration
        val hvccBox = findBox(
            primaryPropBoxes,
            HevcConfigurationBox::class.java
        )
            ?: throw IOException("HevcConfigurationBox('hvcC') not found")
        val hevcConfig = hvccBox.hevcDecoderConfigurationRecord
        val baos = ByteArrayOutputStream()
        val startCode = byteArrayOf(0x00, 0x00, 0x00, 0x01)
        for (params in hevcConfig.arrays) {
            for (nalUnit in params.nalUnits) {
                baos.write(startCode)
                baos.write(nalUnit)
            }
        }
        info.paramset = ByteBuffer.wrap(baos.toByteArray())
        Log.d(TAG, "HEIC HEVC profile=" + hevcConfig.general_profile_idc
                    + " level=" + hevcConfig.general_level_idc / 30f
                    + " bitDepth=" + (hevcConfig.bitDepthLumaMinus8 + 8)
        )
        if (hevcConfig.lengthSizeMinusOne + 1 != 4) {
            throw IOException("unsupported DecoderConfigurationRecord.LengthSizeMinusOne("
                        + hevcConfig.lengthSizeMinusOne + ")"
            )
        }

        // get bitstream position
        val ilocBoxes = isoFile.getBoxes(
            ItemLocationBox::class.java,
            true
        )
        if (ilocBoxes.isEmpty()) {
            throw IOException("ItemLocationBox('iloc') not found")
        }
        val ilocBox = ilocBoxes[0]
        ilocBox.parseDetails()
        for (item in ilocBox.getItems()) {
            if (item.itemId == pitmBox.itemId) {
                info.offset = item.baseOffset.toInt() + item.extents?.get(0)?.extentOffset?.toInt()!!
                info.length = item.extents!![0].extentLength.toInt()
                break
            }
        }
        Log.d(TAG, "HEIC bitstream offset=" + info.offset + " length=" + info.length)
        return info
    }

    private fun extractBitstream(
        heif: ByteArray,
        info: ImageInfo
    ): ByteBuffer {
        // extract HEVC bitstream
        val bitstream = ByteBuffer.allocate(info.length)
            .put(heif, info.offset, info.length)
            .order(ByteOrder.BIG_ENDIAN)
        bitstream.rewind()
        // convert hvcC format to Annex.B format
        do {
            val pos = bitstream.position()
            val size = bitstream.int // hevcConfig.getLengthSizeMinusOne()==3
            bitstream.position(pos)
            bitstream.putInt(1) // start_code={0x00 0x00 0x00 0x01}
            bitstream.position(bitstream.position() + size)
        } while (bitstream.remaining() > 0)
        bitstream.rewind()
        return bitstream
    }

    private fun <T : Box?> findBox(
        container: List<Box>,
        clazz: Class<T>
    ): T? {
        for (box in container) {
            if (clazz.isInstance(box)) {
                return box as T
            }
        }
        return null
    }

    private fun configureDecoder(
        info: ImageInfo,
        maxInputSize: Int,
        surface: Surface
    ): MediaCodec {
        if (mDecoderSupportedSize!!.width < info.size!!.width || mDecoderSupportedSize!!.height < info.size!!.height) {
            Log.w(TAG, "HEVC image may exceed decoder capability")
        }
        return try {
            val decoder = MediaCodec.createByCodecName(mDecoderName!!)
            val inputFormat = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_HEVC, info.size!!.width, info.size!!.height
            )
            inputFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxInputSize)
            inputFormat.setByteBuffer("csd-0", info.paramset)
            Log.d(TAG, "HEVC input-format=$inputFormat")
            decoder.configure(inputFormat, surface, null, 0)
            decoder
        } catch (ex: IOException) {
            throw RuntimeException("no HEVC decoding support")
        }
    }

    @Throws(FormatFallbackException::class)
    private suspend fun renderHevcImageWithFormat(
        bitstream: ByteBuffer,
        info: ImageInfo,
        imageFormat: Int
    ): Bitmap {
        ImageReader.newInstance(
            info.size!!.width,
            info.size!!.height,
            imageFormat,
            1
        ).use { reader ->
            val bitmapDeferred = CoroutineScope(Dispatchers.Default).async {
                renderHevcImage(bitstream, info, reader.surface)
                var image: Image? = null

                return@async try {
                    image = try {
                        reader.acquireNextImage()
                    } catch (ex: UnsupportedOperationException) {
                        throw FormatFallbackException(ex)
                    }
                    when (image.format) {
                        ImageFormat.YUV_420_888, ImageFormat.YV12 -> convertYuv420ToBitmap(image)
                        ImageFormat.RGB_565 -> convertRgb565ToBitmap(image)
                        else -> throw RuntimeException("unsupported image format(" + image.format + ")")
                    }
                } finally {
                    image?.close()
                }
            }
            return@renderHevcImageWithFormat bitmapDeferred.await()
        }
    }

    private suspend fun renderHevcImage(
        bitstream: ByteBuffer,
        info: ImageInfo,
        surface: Surface
    ) {
        val beginTime = SystemClock.elapsedRealtimeNanos()

        // configure HEVC decoder
        val decoder = configureDecoder(info, bitstream.limit(), surface)
        var outputFormat = decoder.outputFormat
        Log.d(TAG, "HEVC output-format=$outputFormat")
        decoder.start()
        try {
            // set bitstream to decoder
            var inputBufferId = decoder.dequeueInputBuffer(-1)
            check(inputBufferId >= 0) { "dequeueInputBuffer return $inputBufferId" }
            val inBuffer = decoder.getInputBuffer(inputBufferId)
            inBuffer!!.put(bitstream)
            decoder.queueInputBuffer(inputBufferId, 0, bitstream.limit(), 0, 0)

            // notify end of stream
            inputBufferId = decoder.dequeueInputBuffer(-1)
            check(inputBufferId >= 0) { "dequeueInputBuffer return $inputBufferId" }
            decoder.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)

            // get decoded image
            val bufferInfo = MediaCodec.BufferInfo()
            while (true) {
                val outputBufferId = decoder.dequeueOutputBuffer(bufferInfo, -1)
                if (outputBufferId >= 0) {
                    decoder.releaseOutputBuffer(outputBufferId, true)
                    break
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    outputFormat = decoder.outputFormat
                    Log.d(TAG, "HEVC output-format=$outputFormat")
                } else {
                    Log.d(TAG,"HEVC dequeueOutputBuffer return $outputBufferId")
                }
            }
            decoder.flush()
        } finally {
            decoder.stop()
            decoder.release()
        }
        val endTime = SystemClock.elapsedRealtimeNanos()
        Log.i(TAG, "HEVC decoding elapsed=" + (endTime - beginTime) / 1000000f + "[msec]")
    }

    private suspend fun convertYuv420ToBitmap(image: Image?): Bitmap {
        val rs = mRenderScript
        val width = image!!.width
        val height = image.height
        val chromaWidth = width / 2
        val chromaHeight = height / 2
        val lumaSize = width * height
        val chromaSize = chromaWidth * chromaHeight

        // prepare input Allocation for RenderScript
        val inType = Type.Builder(rs, Element.U8(rs)).setX(width)
                .setY(height).setYuvFormat(ImageFormat.YV12)
        val inAlloc =
            Allocation.createTyped(rs, inType.create(), Allocation.USAGE_SCRIPT)
        val rawBuffer = ByteArray(inAlloc.bytesSize)
        val planes = image.planes
        val lumaStride = planes[0].rowStride
        val chromaStride = planes[1].rowStride
        if (lumaStride == width) {
            // copy luma plane to rawBuffer (w/o padding)
            planes[0].buffer[rawBuffer, 0, lumaSize]
        } else {
            // copy luma plane to rawBuffer
            val planeBuf = planes[0].buffer
            for (y in 0 until height) {
                planeBuf.position(lumaStride * y)
                planeBuf[rawBuffer, width * y, width]
            }
        }
        if (chromaStride == chromaWidth) {
            // copy chroma planes to rawBuffer (w/o padding)
            planes[1].buffer[rawBuffer, lumaSize, chromaSize]
            planes[2].buffer[rawBuffer, lumaSize + chromaSize, chromaSize]
        } else {
            // copy chroma planes to rawBuffer
            var planeBuf = planes[1].buffer
            for (y in 0 until chromaHeight) {
                planeBuf.position(chromaStride * y)
                planeBuf[rawBuffer, lumaSize + chromaWidth * y, chromaWidth]
            }
            planeBuf = planes[2].buffer
            for (y in 0 until chromaHeight) {
                planeBuf.position(chromaStride * y)
                planeBuf[rawBuffer, lumaSize + chromaSize + chromaWidth * y, chromaWidth]
            }
        }
        inAlloc.copyFromUnchecked(rawBuffer)

        // prepare output Allocation for RenderScript
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val outAlloc = Allocation.createFromBitmap(
            rs,
            bmp,
            Allocation.MipmapControl.MIPMAP_NONE,
            Allocation.USAGE_SCRIPT or Allocation.USAGE_SHARED
        )

        // convert YUV to RGB colorspace
        val converter = ScriptC_yuv2rgb(rs)
        converter._gYUV = inAlloc
        converter.forEach_convert(outAlloc)
        outAlloc.copyTo(bmp)
        return bmp
    }

    private suspend fun convertRgb565ToBitmap(image: Image?): Bitmap {
        val bmp =
            Bitmap.createBitmap(image!!.width, image.height, Bitmap.Config.RGB_565)
        val planes = image.planes
        bmp.copyPixelsFromBuffer(planes[0].buffer)

        return bmp
    }

    internal class ImageInfo {
        var size: Size? = null
        var paramset: ByteBuffer? = null
        var offset = 0
        var length = 0
    }

    private class FormatFallbackException internal constructor(ex: Throwable?) :
        Exception(ex)
}