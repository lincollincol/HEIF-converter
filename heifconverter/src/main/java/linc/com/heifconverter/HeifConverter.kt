package linc.com.heifconverter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat.getExternalFilesDirs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import linc.com.heifconverter.HeifConverter.Format.JPEG
import linc.com.heifconverter.HeifConverter.Format.PNG
import linc.com.heifconverter.HeifConverter.Format.WEBP
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import kotlin.coroutines.resume

class HeifConverter internal constructor(private val context: Context) {

    private var pathToHeicFile: String? = null
    private var url: String? = null
    private var resId: Int? = null
    private var inputStream: InputStream? = null
    private var byteArray: ByteArray? = null

    private var outputQuality: Int = 100
    private var saveResultImage: Boolean = true
    private lateinit var outputFormat: String
    private lateinit var convertedFileName: String
    private lateinit var pathToSaveDirectory: String

    private var fromDataType = InputDataType.NONE

    private val heifReader = HeifReader(context)

    init {
        initDefaultValues()
    }

    fun fromFile(pathToFile: String) : HeifConverter {
        if(!File(pathToFile).exists()) {
            throw FileNotFoundException("HEIC file not found!")
        }
        this.pathToHeicFile = pathToFile
        this.fromDataType = InputDataType.FILE
        return this
    }

    fun fromInputStream(inputStream: InputStream) : HeifConverter {
        this.inputStream = inputStream
        this.fromDataType = InputDataType.INPUT_STREAM
        return this
    }

    fun fromResource(id: Int) : HeifConverter {
        val isResValid = context.resources.getIdentifier(
            context.resources.getResourceName(id),
            "drawable",
            context.packageName
        ) != 0
        if(!isResValid)
            throw FileNotFoundException("Resource not found!")
        this.fromDataType = InputDataType.RESOURCES
        return this
    }

    fun fromUrl(heicImageUrl: String) : HeifConverter {
        this.url = heicImageUrl
        this.fromDataType = InputDataType.URL
        return this
    }

    fun fromByteArray(data: ByteArray) : HeifConverter {
        if(data.isEmpty())
            throw FileNotFoundException("Empty byte array!")
        this.byteArray = data
        this.fromDataType = InputDataType.BYTE_ARRAY
        return this
    }

    fun withOutputFormat(format: String) : HeifConverter {
        this.outputFormat = format
        return this
    }

    fun withOutputQuality(quality: Int) : HeifConverter {
        this.outputQuality = when {
            quality > 100 -> 100
            quality < 0 -> 0
            else -> quality
        }
        return this
    }

    @Deprecated("Will be added in future", ReplaceWith("", ""), DeprecationLevel.HIDDEN)
    fun saveToDirectory(pathToDirectory: String) : HeifConverter {
        if(!File(pathToDirectory).exists()) {
            throw FileNotFoundException("Directory not found!")
        }
        this.pathToSaveDirectory = pathToDirectory
        return this
    }

    fun saveFileWithName(convertedFileName: String) : HeifConverter {
        this.convertedFileName = convertedFileName
        return this
    }

    fun saveResultImage(saveResultImage: Boolean) : HeifConverter {
        this.saveResultImage = saveResultImage
        return this
    }

    fun convert() {
        convert {}
    }

    /**
     * convert using coroutines to get result synchronously
     * @return map of [Key] to values
     */
    suspend fun convertBlocking(): Map<String, Any?> {
        var bitmap: Bitmap? = null

        // Handle Android Q version in every case
        withContext(Dispatchers.IO) {
            bitmap = when (fromDataType) {
                InputDataType.FILE -> {
                    when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> BitmapFactory.decodeFile(pathToHeicFile)
                        else -> heifReader.decodeFile(pathToHeicFile)
                    }
                }
                InputDataType.URL -> {
                    when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                            // Download image
                            val url = URL(url)
                            val connection = url
                                .openConnection() as HttpURLConnection
                            connection.doInput = true
                            connection.connect()
                            val input: InputStream = connection.inputStream
                            BitmapFactory.decodeStream(input)
                        }
                        else -> heifReader.decodeUrl(url!!)
                    }
                }
                InputDataType.RESOURCES -> {
                    when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> BitmapFactory.decodeResource(context.resources, resId!!)
                        else -> heifReader.decodeResource(context.resources, resId!!)
                    }
                }
                InputDataType.INPUT_STREAM -> {
                    when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> BitmapFactory.decodeStream(inputStream!!)
                        else -> heifReader.decodeStream(inputStream!!)
                    }
                }
                InputDataType.BYTE_ARRAY -> {
                    when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> BitmapFactory.decodeByteArray(
                            byteArray!!,
                            0,
                            byteArray!!.size,
                            BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                            }
                        )
                        else -> heifReader.decodeByteArray(byteArray!!)
                    }
                }
                else -> throw IllegalStateException("You forget to pass input type: File, Url etc. Use such functions: fromFile(. . .) etc.")
            }
        }

        val directoryToSave = File(pathToSaveDirectory)
        var dest: File? = File(directoryToSave, "$convertedFileName$outputFormat")

        val result: MutableMap<String, Any?> = mutableMapOf(Key.BITMAP to bitmap)
        withContext(Dispatchers.IO) {
            val out = FileOutputStream(dest!!)
            try {
                bitmap?.compress(useFormat(outputFormat), outputQuality, out)
                if (!saveResultImage) {
                    dest!!.delete()
                    dest = null
                }
                result[Key.IMAGE_PATH] = dest?.path
                    ?: "You set saveResultImage(false). If you want to save file - pass true"
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e : Exception) {
                e.printStackTrace()
            } finally {
                out.flush()
                out.close()
            }
        }

        return result.toMap()
    }

    /**
     * convert asynchronously using [block] to receive the results.
     *
     * @param[coroutineScope] [CoroutineScope] to launch the coroutine in.
     * @param[block] lambda for retrieving the result.
     * @return A reference to the launched coroutine as a [Job], cancel via [Job.cancel].
     */
    fun convert(
        coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main),
        block: (result: Map<String, Any?>) -> Unit,
    ): Job = coroutineScope.launch {
        val result = convertBlocking()
        block(result)
    }

    private fun initDefaultValues() {
        outputFormat = JPEG
        convertedFileName  = UUID.randomUUID().toString()
        pathToSaveDirectory = getExternalFilesDirs(context, Environment.DIRECTORY_DCIM)[0].path
    }

    private fun useFormat(format: String) = when(format) {
        WEBP ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
            else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
        PNG -> Bitmap.CompressFormat.PNG
        else -> Bitmap.CompressFormat.JPEG
    }

    object Format {
        const val JPEG = ".jpg"
        const val PNG = ".png"
        const val WEBP = ".webp"
    }

    object Key {
        const val BITMAP = "converted_bitmap_heic"
        const val IMAGE_PATH = "path_to_converted_heic"
    }

    private enum class InputDataType {
        FILE, URL, RESOURCES, INPUT_STREAM,
        BYTE_ARRAY, NONE
    }

    companion object {

        fun useContext(context: Context) = HeifConverter(context)
    }
}
