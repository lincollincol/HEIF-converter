package linc.com.heifconverter

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.lang.IllegalStateException
import java.util.*

class HeifConverter(
    private val context: Context
) {
    init {
        HeifReader.initialize(context)
    }

    fun convertHeicTo(
        format: String,
        pathToHeicFile: String,
        pathToSaveDirectory: String = ContextCompat
            .getExternalFilesDirs(context, Environment.DIRECTORY_DCIM)[0].path,
        convertedFileName: String = UUID.randomUUID().toString(),
        quality: Int = 100
    ) : Bitmap? {

        if(!File(pathToHeicFile).exists()) {
            throw FileNotFoundException("HEIC file not found!")
        }

        val directoryToSave = File(pathToSaveDirectory)

        if(!directoryToSave.exists()) {
            throw FileNotFoundException("Directory not found!")
        }

        val dest = File(directoryToSave, "$convertedFileName$format")

        return try {
            val out = FileOutputStream(dest)
            val bitmap = HeifReader.decodeFile(pathToHeicFile)
            bitmap?.compress(useFormat(format), quality, out)
            out.flush()
            out.close()
            bitmap
        } catch (e : Exception) {
            e.printStackTrace();
            null
        }
    }

    private fun useFormat(format: String) = when(format) {
        WEBP -> Bitmap.CompressFormat.WEBP
        PNG -> Bitmap.CompressFormat.PNG
        else -> Bitmap.CompressFormat.JPEG
    }

    companion object Format {
        const val JPEG = ".jpg"
        const val PNG = ".png"
        const val WEBP = ".webp"
    }
}