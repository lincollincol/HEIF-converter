package linc.com.heifconverter

import android.Manifest
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.getExternalFilesDirs
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.produce
import linc.com.heifconverter.HeifConverter.Format.JPEG
import linc.com.heifconverter.HeifConverter.Format.PNG
import linc.com.heifconverter.HeifConverter.Format.WEBP
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.InvalidPathException
import java.util.*

object HeifConverter{

    private lateinit var context: Context

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

    fun useContext(context: Context) : HeifConverter {
        this.context = context
        HeifReader.initialize(HeifConverter.context)
        ActivityCompat.requestPermissions(
            (context as AppCompatActivity),
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            123
        )
        initDefaultValues()
        return this
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

    fun convert(block: (result: Map<String, Any?>) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            var bitmap: Bitmap? = null
            withContext(Dispatchers.IO) {
                bitmap = when(fromDataType) {
                    InputDataType.FILE -> HeifReader.decodeFile(pathToHeicFile)
                    InputDataType.URL -> HeifReader.decodeUrl(url!!)
                    InputDataType.RESOURCES -> HeifReader.decodeResource(context.resources, resId!!)
                    InputDataType.INPUT_STREAM -> HeifReader.decodeStream(inputStream!!)
                    InputDataType.BYTE_ARRAY -> HeifReader.decodeByteArray(byteArray!!)
                    else -> throw IllegalStateException("You forget to pass input type: File, Url etc. Use such functions: fromFile(. . .) etc.")
                }
            }

            val directoryToSave = File(pathToSaveDirectory)
            var dest: File? = File(directoryToSave, "$convertedFileName$outputFormat")

            withContext(Dispatchers.IO) {
                val out = FileOutputStream(dest!!)
                try {
                    bitmap?.compress(useFormat(outputFormat), outputQuality, out)
                    if(!saveResultImage) {
                        dest!!.delete()
                        dest = null
                    }
                    withContext(Dispatchers.Main) {
                        block(mapOf(
                            Key.BITMAP to bitmap,
                            Key.IMAGE_PATH to (dest?.path ?: "You set saveResultImage(false). If you want to save file - pass true")))
                    }
                } catch (e : Exception) {
                    e.printStackTrace()
                }finally {
                    out.flush()
                    out.close()
                }
            }
        }
    }

    private fun initDefaultValues() {
        outputFormat = JPEG
        convertedFileName  = UUID.randomUUID().toString()
        pathToSaveDirectory = getExternalFilesDirs(context, Environment.DIRECTORY_DCIM)[0].path
    }

    private fun useFormat(format: String) = when(format) {
        WEBP -> Bitmap.CompressFormat.WEBP
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
}