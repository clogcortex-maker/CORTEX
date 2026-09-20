package com.cortex.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cortex.app.ui.Themes
import com.cortex.app.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ImageDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PATH = "extra_path"
        const val EXTRA_PROMPT = "extra_prompt"
    }

    private var imagePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Themes.currentStyleRes(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_detail)

        imagePath = intent.getStringExtra(EXTRA_PATH)
        val prompt = intent.getStringExtra(EXTRA_PROMPT) ?: ""
        val image = findViewById<ImageView>(R.id.detailImage)
        val promptText = findViewById<TextView>(R.id.detailPrompt)

        promptText.text = prompt
        promptText.visibility = if (prompt.isEmpty()) View.GONE else View.VISIBLE
        imagePath?.let { ImageUtils.decodeScaled(it, 2048)?.let { b -> image.setImageBitmap(b) } }

        findViewById<View>(R.id.detailSave).setOnClickListener {
            imagePath?.let { path ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val ok = ImageUtils.saveToGallery(this@ImageDetailActivity, File(path))
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ImageDetailActivity,
                            if (ok) R.string.saved_to_gallery else R.string.save_failed,
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        findViewById<View>(R.id.detailShare).setOnClickListener {
            imagePath?.let { ImageUtils.shareImage(this@ImageDetailActivity, File(it)) }
        }
        findViewById<View>(R.id.detailRefine).setOnClickListener {
            val result = Intent().putExtra(EXTRA_PATH, imagePath)
            setResult(RESULT_OK, result)
            finish()
        }
    }
}
