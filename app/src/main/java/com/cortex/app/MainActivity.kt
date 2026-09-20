package com.cortex.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cortex.app.chat.ChatAdapter
import com.cortex.app.chat.ChatMessage
import com.cortex.app.chat.ChatStore
import com.cortex.app.databinding.ActivityMainBinding
import com.cortex.app.engine.Condition
import com.cortex.app.engine.CortexEngine
import com.cortex.app.engine.ModelManager
import com.cortex.app.ui.Themes
import com.cortex.app.util.ImageUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var modelManager: ModelManager
    private lateinit var chatStore: ChatStore
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter
    private val idGen = AtomicLong(System.currentTimeMillis())

    private var engineReady = false
    private var generatingJob: Job? = null
    private var pendingGuidePath: String? = null
    private var setupRunning = false
    private var appliedThemeKey: String = ""
    private var engineFailed = false
    private var queuedPrompt: String? = null

    private val engine: CortexEngine by lazy { CortexEngine(this) }

    private val callbacks = object : ChatAdapter.Callbacks {
        override fun onRefine(message: ChatMessage) {
            message.imagePath?.let { showRefineDialog("", it) }
        }
        override fun onSave(message: ChatMessage) { saveToGallery(message) }
        override fun onShare(message: ChatMessage) { shareImage(message) }
        override fun onView(message: ChatMessage) { openDetail(message) }
        override fun onCancel() { cancelGeneration() }
    }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) attachGuide(uri)
        }

    private val detailLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.getStringExtra(ImageDetailActivity.EXTRA_PATH)?.let { showRefineDialog("", it) }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        appliedThemeKey = Themes.currentKey(this)
        setTheme(Themes.currentStyleRes(this))
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        modelManager = ModelManager(this)
        chatStore = ChatStore(this)

        adapter = ChatAdapter(messages, callbacks)
        binding.chatRecycler.layoutManager =
            LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.chatRecycler.adapter = adapter

        messages.addAll(chatStore.load())
        if (messages.isEmpty()) {
            addMessage(ChatMessage(nextId(), ChatMessage.Role.ASSISTANT_TEXT,
                text = getString(R.string.welcome)))
        }

        binding.sendBtn.setOnClickListener { onSend() }
        binding.attachBtn.setOnClickListener { pickImageLauncher.launch("image/*") }
        binding.removeGuideBtn.setOnClickListener { clearPendingGuide() }
        binding.setupBtn.setOnClickListener { startSetup() }
        binding.toolbar.inflateMenu(R.menu.menu_main)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java)); true
                }
                else -> false
            }
        }

        if (modelManager.isBaseReady()) {
            binding.setupOverlay.visibility = View.GONE
            initEngine()
        } else {
            binding.setupOverlay.visibility = View.VISIBLE
        }
    }

    override fun onStart() {
        super.onStart()
        if (appliedThemeKey != Themes.currentKey(this)) recreate()
    }

    override fun onDestroy() {
        generatingJob?.cancel()
        engine.closeQuietly()
        super.onDestroy()
    }

    private fun nextId(): Long = idGen.incrementAndGet()

    private fun addMessage(m: ChatMessage): ChatMessage {
        messages.add(m)
        adapter.notifyItemInserted(messages.size - 1)
        binding.chatRecycler.scrollToPosition(messages.size - 1)
        persist()
        return m
    }

    private fun updateMessage(m: ChatMessage) {
        val i = messages.indexOf(m)
        if (i >= 0) adapter.notifyItemChanged(i)
        binding.chatRecycler.scrollToPosition(messages.size - 1)
    }

    private fun removeMessage(m: ChatMessage) {
        val i = messages.indexOf(m)
        if (i >= 0) {
            messages.removeAt(i)
            adapter.notifyItemRemoved(i)
        }
        persist()
    }

    private fun persist() = chatStore.save(messages.filter { it.role != ChatMessage.Role.PROGRESS })

    // ---------------- engine ----------------

    private fun initEngine() {
        binding.statusChip.text = getString(R.string.status_loading_model)
        binding.statusChip.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                engine.ensurePlain(modelManager.modelDir.absolutePath)
                engineReady = true
                engineFailed = false
                withContext(Dispatchers.Main) {
                    binding.statusChip.visibility = View.GONE
                    queuedPrompt?.let { p ->
                        queuedPrompt = null
                        startGeneration(p, null, null, recordUserMessage = false)
                    }
                }
            } catch (t: Throwable) {
                engineReady = false
                engineFailed = true
                withContext(Dispatchers.Main) {
                    binding.statusChip.visibility = View.GONE
                    addMessage(ChatMessage(nextId(), ChatMessage.Role.ASSISTANT_TEXT,
                        text = getString(R.string.engine_init_failed, t.message ?: "unknown"),
                        error = true))
                }
            }
        }
    }

    // ---------------- setup ----------------

    private fun startSetup() {
        if (setupRunning) return
        setupRunning = true
        binding.setupBtn.isEnabled = false
        binding.setupBtn.text = getString(R.string.setup_downloading)
        lifecycleScope.launch {
            try {
                modelManager.downloadBase { stage, pct ->
                    runOnUiThread {
                        binding.setupProgress.isIndeterminate = false
                        binding.setupProgress.progress = pct
                        val stageText = when (stage) {
                            "download" -> getString(R.string.setup_stage_download)
                            "unzip" -> getString(R.string.setup_stage_unzip)
                            "plugins" -> getString(R.string.setup_stage_plugins)
                            else -> ""
                        }
                        binding.setupStatus.text = getString(R.string.setup_status, stageText, pct)
                    }
                }
                binding.setupOverlay.visibility = View.GONE
                initEngine()
            } catch (t: Throwable) {
                binding.setupStatus.text = getString(R.string.setup_failed, t.message ?: "")
                binding.setupBtn.isEnabled = true
                binding.setupBtn.text = getString(R.string.setup_retry)
            } finally {
                setupRunning = false
            }
        }
    }

    // ---------------- generation ----------------

    private fun onSend() {
        val prompt = binding.inputText.text.toString().trim()
        if (prompt.isEmpty()) return
        if (generatingJob?.isActive == true) {
            Toast.makeText(this, R.string.busy, Toast.LENGTH_SHORT).show(); return
        }
        binding.inputText.setText("")
        val userMsg = ChatMessage(nextId(), ChatMessage.Role.USER, text = prompt)

        if (!engineReady) {
            addMessage(userMsg)
            when {
                !modelManager.isBaseReady() -> {
                    binding.setupOverlay.visibility = View.VISIBLE
                    addMessage(ChatMessage(nextId(), ChatMessage.Role.ASSISTANT_TEXT,
                        text = getString(R.string.need_setup)))
                }
                engineFailed -> addMessage(ChatMessage(nextId(), ChatMessage.Role.ASSISTANT_TEXT,
                    text = getString(R.string.engine_failed_send), error = true))
                else -> {
                    queuedPrompt = prompt
                    addMessage(ChatMessage(nextId(), ChatMessage.Role.ASSISTANT_TEXT,
                        text = getString(R.string.engine_loading_queued)))
                }
            }
            return
        }

        addMessage(userMsg)
        val guide = pendingGuidePath
        if (guide != null) {
            clearPendingGuide()
            showRefineDialog(prompt, guide)
        } else {
            startGeneration(prompt, null, null, recordUserMessage = false)
        }
    }

    private fun showRefineDialog(prefill: String, guidePath: String) {
        val view = layoutInflater.inflate(R.layout.dialog_refine, null)
        val input = view.findViewById<EditText>(R.id.refinePrompt)
        val radio = view.findViewById<RadioGroup>(R.id.conditionGroup)
        input.setText(prefill)
        AlertDialog.Builder(this)
            .setTitle(R.string.refine_title)
            .setView(view)
            .setPositiveButton(R.string.generate) { _, _ ->
                val p = input.text.toString().trim()
                if (p.isEmpty()) return@setPositiveButton
                val cond = when (radio.checkedRadioButtonId) {
                    R.id.condDepth -> Condition.DEPTH
                    R.id.condFace -> Condition.FACE
                    else -> Condition.EDGE
                }
                startGeneration(p, guidePath, cond)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startGeneration(
        prompt: String,
        guidePath: String?,
        condition: Condition?,
        recordUserMessage: Boolean = true
    ) {
        val prefs = getSharedPreferences("cortex_prefs", MODE_PRIVATE)
        val iterations = prefs.getInt("iterations", 20)
        val displayEvery = prefs.getInt("display_every", 5).coerceAtLeast(1)
        val seed = if (prefs.getBoolean("seed_random", true))
            (1000..999999).random() else prefs.getInt("seed_fixed", 42)

        if (recordUserMessage) {
            addMessage(ChatMessage(nextId(), ChatMessage.Role.USER, text = prompt,
                guidePath = guidePath, condition = condition?.wire))
        }
        val progressMsg = addMessage(ChatMessage(nextId(), ChatMessage.Role.PROGRESS,
            text = getString(R.string.progress_starting), totalSteps = iterations, inProgress = true))

        generatingJob = lifecycleScope.launch(Dispatchers.Default) {
            try {
                var last: android.graphics.Bitmap? = null
                if (guidePath != null && condition != null) {
                    if (!modelManager.isPluginReady(condition)) {
                        withContext(Dispatchers.Main) {
                            progressMsg.text = getString(R.string.downloading_plugin)
                            updateMessage(progressMsg)
                        }
                        modelManager.ensurePlugin(condition) { }
                    }
                    val guideBmp = ImageUtils.decodeScaledTo512(guidePath)
                        ?: throw IllegalStateException(getString(R.string.err_guide_read))
                    engine.ensureConditioned(modelManager.modelDir.absolutePath, modelManager)
                    engine.setConditionedInputs(prompt, guideBmp,
                        engine.toConditionType(condition), iterations, seed)
                } else {
                    engine.ensurePlain(modelManager.modelDir.absolutePath)
                    engine.setInputs(prompt, iterations, seed)
                }
                for (step in 1..iterations) {
                    ensureActive()
                    val show = (step % displayEvery == 0) || step == iterations
                    val bmp = engine.execute(show)
                    if (show && bmp != null) {
                        last = bmp
                        val previewFile = savePreview(bmp!!)
                        withContext(Dispatchers.Main) {
                            progressMsg.step = step
                            progressMsg.previewPath = previewFile
                            progressMsg.text = ""
                            updateMessage(progressMsg)
                        }
                    } else if (step % 2 == 0) {
                        withContext(Dispatchers.Main) {
                            progressMsg.step = step
                            updateMessage(progressMsg)
                        }
                    }
                }
                val finalBmp = last
                    ?: throw IllegalStateException(getString(R.string.err_no_image))
                val f = ImageUtils.saveBitmapPng(finalBmp, modelManager.imagesDir,
                    "cortex_${System.currentTimeMillis()}.png")
                withContext(Dispatchers.Main) {
                    removeMessage(progressMsg)
                    val label = buildString {
                        append(getString(R.string.image_meta, seed, iterations))
                        if (condition != null) append(" · ${condition.wire}")
                    }
                    addMessage(ChatMessage(nextId(), ChatMessage.Role.ASSISTANT_IMAGE,
                        text = label, imagePath = f.absolutePath))
                }
            } catch (c: CancellationException) {
                withContext(Dispatchers.Main) {
                    removeMessage(progressMsg)
                    addMessage(ChatMessage(nextId(), ChatMessage.Role.ASSISTANT_TEXT,
                        text = getString(R.string.cancelled)))
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    removeMessage(progressMsg)
                    addMessage(ChatMessage(nextId(), ChatMessage.Role.ASSISTANT_TEXT,
                        text = getString(R.string.generation_failed,
                            t.message ?: t.javaClass.simpleName),
                        error = true))
                }
            }
        }
    }

    private fun cancelGeneration() {
        generatingJob?.cancel()
        Toast.makeText(this, R.string.cancelling, Toast.LENGTH_SHORT).show()
    }

    private fun savePreview(bmp: android.graphics.Bitmap): String {
        val f = File(cacheDir, "preview.png")
        java.io.FileOutputStream(f).use { out ->
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, out)
        }
        return f.absolutePath
    }

    // ---------------- guide image ----------------

    private fun attachGuide(uri: android.net.Uri) {
        try {
            val f = File(cacheDir, "guide_${System.currentTimeMillis()}.img")
            contentResolver.openInputStream(uri)?.use { input ->
                f.outputStream().use { input.copyTo(it) }
            } ?: return
            pendingGuidePath = f.absolutePath
            ImageUtils.decodeScaled(f.absolutePath, 256)?.let {
                binding.guideThumb.setImageBitmap(it)
            }
            binding.guideRow.visibility = View.VISIBLE
        } catch (t: Throwable) {
            Toast.makeText(this, R.string.err_attach, Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearPendingGuide() {
        pendingGuidePath = null
        binding.guideRow.visibility = View.GONE
    }

    // ---------------- actions ----------------

    private fun saveToGallery(message: ChatMessage) {
        val path = message.imagePath ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val ok = ImageUtils.saveToGallery(this@MainActivity, File(path))
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity,
                    if (ok) R.string.saved_to_gallery else R.string.save_failed,
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareImage(message: ChatMessage) {
        val path = message.imagePath ?: return
        try {
            ImageUtils.shareImage(this, File(path))
        } catch (t: Throwable) {
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDetail(message: ChatMessage) {
        val path = message.imagePath ?: return
        detailLauncher.launch(
            Intent(this, ImageDetailActivity::class.java)
                .putExtra(ImageDetailActivity.EXTRA_PATH, path)
                .putExtra(ImageDetailActivity.EXTRA_PROMPT, message.text))
    }
}
