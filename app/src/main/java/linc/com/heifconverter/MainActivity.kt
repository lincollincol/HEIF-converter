package linc.com.heifconverter

import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        convert.setOnClickListener {
            HeifConverter.useContext(this)
                .fromUrl("https://github.com/nokiatech/heif/raw/gh-pages/content/images/crowd_1440x960.heic")
                .withOutputFormat(HeifConverter.Format.PNG)
                .withOutputQuality(100)
                .saveFileWithName("Image_Converted_Name_2")
                .saveResultImage(true)
                .convert {
                    println(it[HeifConverter.Key.IMAGE_PATH] as String)
                    resultImage.setImageBitmap((it[HeifConverter.Key.BITMAP] as Bitmap))
                }
        }




    }
}
