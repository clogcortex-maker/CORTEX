package com.cortex.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.cortex.app.chat.ChatStore
import com.cortex.app.databinding.ActivitySettingsBinding
import com.cortex.app.engine.ModelManager
import com.cortex.app.ui.Themes

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var modelManager: ModelManager

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Themes.currentStyleRes(this))
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        modelManager = ModelManager(this)

        val prefs = getSharedPreferences("cortex_prefs", MODE_PRIVATE)

        // ---- theme picker ----
        val current = Themes.currentKey(this)
        Themes.ALL.forEach { theme ->
            val rb = LayoutInflater.from(this)
                .inflate(R.layout.item_theme_choice, binding.themeGroup, false) as android.widget.RadioButton
            rb.text = theme.name
            rb.isChecked = theme.key == current
            rb.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    Themes.apply(this, theme.key)
                    Toast.makeText(this, R.string.theme_applied, Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                }
            }
            binding.themeGroup.addView(rb)
        }

        // ---- iterations ----
        val iterations = prefs.getInt("iterations", 20)
        binding.iterationsValue.text = iterations.toString()
        binding.iterationsBar.max = 40 // 10..50
        binding.iterationsBar.progress = iterations - 10
        binding.iterationsBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                val v = value + 10
                binding.iterationsValue.text = v.toString()
                prefs.edit().putInt("iterations", v).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // ---- progress preview frequency ----
        val displayEvery = prefs.getInt("display_every", 5)
        binding.displayValue.text = displayEvery.toString()
        binding.displayBar.max = 9 // 1..10
        binding.displayBar.progress = displayEvery - 1
        binding.displayBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                val v = value + 1
                binding.displayValue.text = v.toString()
                prefs.edit().putInt("display_every", v).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // ---- seed ----
        val randomSeed = prefs.getBoolean("seed_random", true)
        binding.seedRandom.isChecked = randomSeed
        binding.seedRandom.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("seed_random", checked).apply()
            binding.seedFixedInput.isEnabled = !checked
            binding.seedFixedInput.alpha = if (checked) 0.4f else 1f
        }
        binding.seedFixedInput.setText(prefs.getInt("seed_fixed", 42).toString())
        binding.seedFixedInput.isEnabled = !randomSeed
        binding.seedFixedInput.alpha = if (randomSeed) 0.4f else 1f
        binding.seedFixedInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val v = binding.seedFixedInput.text.toString().toIntOrNull() ?: 42
                prefs.edit().putInt("seed_fixed", v).apply()
            }
        }

        // ---- data ----
        binding.clearChatBtn.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.confirm)
                .setMessage(R.string.clear_chat_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    ChatStore(this).clear()
                    Toast.makeText(this, R.string.chat_cleared, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        binding.deleteModelsBtn.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.confirm)
                .setMessage(R.string.delete_models_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    modelManager.deleteAll()
                    Toast.makeText(this, R.string.models_deleted, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
}
