package com.cortex.app.chat

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cortex.app.R
import com.cortex.app.util.ImageUtils

class ChatAdapter(
    private val messages: MutableList<ChatMessage>,
    private val callbacks: Callbacks
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface Callbacks {
        fun onRefine(message: ChatMessage)
        fun onSave(message: ChatMessage)
        fun onShare(message: ChatMessage)
        fun onView(message: ChatMessage)
        fun onCancel()
    }

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_TEXT = 1
        private const val TYPE_IMAGE = 2
        private const val TYPE_PROGRESS = 3
    }

    override fun getItemCount(): Int = messages.size

    override fun getItemViewType(position: Int): Int = when (messages[position].role) {
        ChatMessage.Role.USER -> TYPE_USER
        ChatMessage.Role.ASSISTANT_TEXT -> TYPE_TEXT
        ChatMessage.Role.ASSISTANT_IMAGE -> TYPE_IMAGE
        ChatMessage.Role.PROGRESS -> TYPE_PROGRESS
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER -> UserHolder(inf.inflate(R.layout.item_user_msg, parent, false))
            TYPE_IMAGE -> ImageHolder(inf.inflate(R.layout.item_image_card, parent, false))
            TYPE_PROGRESS -> ProgressHolder(inf.inflate(R.layout.item_progress, parent, false))
            else -> TextHolder(inf.inflate(R.layout.item_assistant_text, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val m = messages[position]
        when (holder) {
            is UserHolder -> {
                holder.text.text = m.text
                val guide = m.guidePath
                if (guide != null) {
                    holder.guideThumb.visibility = View.VISIBLE
                    loadThumb(holder.guideThumb, guide)
                } else holder.guideThumb.visibility = View.GONE
            }
            is TextHolder -> {
                holder.text.text = m.text
                if (m.error) {
                    holder.text.setTextColor(
                        androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.error_text))
                } else {
                    val tv = android.util.TypedValue()
                    holder.itemView.context.theme.resolveAttribute(
                        com.google.android.material.R.attr.colorOnSurface, tv, true)
                    holder.text.setTextColor(tv.data)
                }
            }
            is ImageHolder -> {
                val path = m.imagePath
                if (path != null) {
                    loadThumb(holder.image, path)
                }
                holder.caption.text = m.text
                holder.refineBtn.setOnClickListener { callbacks.onRefine(m) }
                holder.saveBtn.setOnClickListener { callbacks.onSave(m) }
                holder.shareBtn.setOnClickListener { callbacks.onShare(m) }
                holder.image.setOnClickListener { callbacks.onView(m) }
            }
            is ProgressHolder -> {
                if (m.totalSteps > 0) {
                    holder.progress.max = m.totalSteps
                    holder.progress.progress = m.step
                    holder.stepText.text = holder.itemView.context.getString(
                        R.string.progress_step, m.step, m.totalSteps)
                } else {
                    holder.stepText.text = m.text
                }
                holder.cancelBtn.setOnClickListener { callbacks.onCancel() }
                val previewPath = m.previewPath
                if (previewPath != null) {
                    holder.preview.visibility = View.VISIBLE
                    loadThumb(holder.preview, previewPath)
                } else {
                    holder.preview.visibility = View.GONE
                }
            }
        }
    }

    private fun loadThumb(view: ImageView, path: String) {
        val bmp: Bitmap? = ImageUtils.decodeScaled(path, 720)
        if (bmp != null) view.setImageBitmap(bmp)
    }

    class UserHolder(v: View) : RecyclerView.ViewHolder(v) {
        val text: TextView = v.findViewById(R.id.msgText)
        val guideThumb: ImageView = v.findViewById(R.id.guideThumb)
    }

    class TextHolder(v: View) : RecyclerView.ViewHolder(v) {
        val text: TextView = v.findViewById(R.id.msgText)
    }

    class ImageHolder(v: View) : RecyclerView.ViewHolder(v) {
        val image: ImageView = v.findViewById(R.id.image)
        val caption: TextView = v.findViewById(R.id.caption)
        val refineBtn: View = v.findViewById(R.id.refineBtn)
        val saveBtn: View = v.findViewById(R.id.saveBtn)
        val shareBtn: View = v.findViewById(R.id.shareBtn)
    }

    class ProgressHolder(v: View) : RecyclerView.ViewHolder(v) {
        val stepText: TextView = v.findViewById(R.id.stepText)
        val progress: android.widget.ProgressBar = v.findViewById(R.id.stepProgress)
        val preview: ImageView = v.findViewById(R.id.preview)
        val cancelBtn: View = v.findViewById(R.id.cancelBtn)
    }
}
