package linc.com.heifconverter

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val hc = HeifConverter(this)

        hc.convertHeicTo(
            HeifConverter.PNG,
            "/storage/9016-4EF8/sample1.heic"
        )

    }

}
