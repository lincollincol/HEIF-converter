package linc.com.heifconverter.sample

import android.graphics.Bitmap
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import linc.com.heifconverter.HeifConverter

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        convert.setOnClickListener {
            HeifConverter.useContext(this)
                .fromUrl("https://github.com/nokiatech/heif/raw/gh-pages/content/images/crowd_1440x960.heic")
                .withOutputFormat(HeifConverter.Format.PNG)
                .withOutputQuality(100)
                .saveFileWithName("Image_Converted_Name")
                .saveResultImage(true)
                .convert {
                    println(it[HeifConverter.Key.IMAGE_PATH] as String)
                    resultImage.setImageBitmap((it[HeifConverter.Key.BITMAP] as Bitmap))
                }
        }
    }
}
