package com.interli.plural

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.load
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.net.URL
import java.util.regex.Pattern

object MediaEmbedHelper {

    private val YOUTUBE_PATTERN = Pattern.compile(
        "(?:https?://)?(?:www\\.)?(?:youtube\\.com/(?:[^/\\n\\s]+/[^/\\n\\s]+/|(?:v|e(?:mbed)?)/|\\S*?[?&]v=)|youtu\\.be/)([a-zA-Z0-9_-]{11})",
        Pattern.CASE_INSENSITIVE
    )

    private val SPOTIFY_PATTERN = Pattern.compile(
        "(?:https?://)?open\\.spotify\\.com/(track|album|playlist|artist|episode|show)/([a-zA-Z0-9]+)",
        Pattern.CASE_INSENSITIVE
    )

    private val metadataCache = mutableMapOf<String, EmbedMetadata>()
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    data class MediaInfo(val type: String, val id: String, val url: String, val category: String? = null)
    data class EmbedMetadata(val title: String?, val thumbnail_url: String?)

    fun findMedia(content: String): List<MediaInfo> {
        val results = mutableListOf<MediaInfo>()
        val ytMatcher = YOUTUBE_PATTERN.matcher(content)
        while (ytMatcher.find()) {
            results.add(MediaInfo("YOUTUBE", ytMatcher.group(1)!!, ytMatcher.group(0)!!))
        }
        val spMatcher = SPOTIFY_PATTERN.matcher(content)
        while (spMatcher.find()) {
            results.add(MediaInfo("SPOTIFY", spMatcher.group(2)!!, spMatcher.group(0)!!, spMatcher.group(1)))
        }
        return results.distinctBy { it.url }
    }

    fun addEmbedsToContainer(container: LinearLayout, content: String) {
        val mediaList = findMedia(content)
        container.removeAllViews()
        if (mediaList.isEmpty()) {
            container.visibility = View.GONE
            return
        }
        container.visibility = View.VISIBLE

        mediaList.forEach { info ->
            val context = container.context
            val card = MaterialCardView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (12 * context.resources.displayMetrics.density).toInt()
                    bottomMargin = (4 * context.resources.displayMetrics.density).toInt()
                }
                radius = (12 * context.resources.displayMetrics.density)
                cardElevation = (4 * context.resources.displayMetrics.density)
                strokeWidth = 1
                strokeColor = 0x11000000
                setCardBackgroundColor(ColorHelper.getBgColor(context))
            }

            val rootLayout = FrameLayout(context)
            
            val previewLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            val thumbnailContainer = FrameLayout(context)
            val bigImage = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (200 * context.resources.displayMetrics.density).toInt()
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0x11000000)
            }
            thumbnailContainer.addView(bigImage)

            val playIcon = ImageView(context).apply {
                setImageResource(android.R.drawable.ic_media_play)
                layoutParams = FrameLayout.LayoutParams(
                    (64 * context.resources.displayMetrics.density).toInt(),
                    (64 * context.resources.displayMetrics.density).toInt()
                ).apply {
                    gravity = android.view.Gravity.CENTER
                }
                alpha = 0.9f
                setPadding(16, 16, 16, 16)
                setBackgroundResource(android.R.drawable.presence_online)
                background?.setTint(0xCC000000.toInt())
            }
            thumbnailContainer.addView(playIcon)
            previewLayout.addView(thumbnailContainer)

            val textLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16.dpToPx(context), 12.dpToPx(context), 16.dpToPx(context), 12.dpToPx(context))
            }

            val titleTv = TextView(context).apply {
                text = if (info.type == "YOUTUBE") "YouTube Video" else "Spotify ${info.category?.lowercase()?.replaceFirstChar { it.uppercase() }}"
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(ColorHelper.getTextColor(context))
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            
            val sourceTv = TextView(context).apply {
                text = if (info.type == "YOUTUBE") "YouTube" else "Spotify"
                textSize = 12f
                setTextColor(if (info.type == "YOUTUBE") 0xFFFF0000.toInt() else 0xFF1DB954.toInt())
                setTypeface(null, android.graphics.Typeface.BOLD)
                alpha = 0.8f
            }

            textLayout.addView(sourceTv)
            textLayout.addView(titleTv)
            previewLayout.addView(textLayout)
            rootLayout.addView(previewLayout)

            card.setOnClickListener {
                startPlayer(rootLayout, previewLayout, info)
            }

            card.addView(rootLayout)
            container.addView(card)

            loadMetadata(info, bigImage, titleTv)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun startPlayer(root: FrameLayout, preview: View, info: MediaInfo) {
        val context = root.context
        preview.visibility = View.GONE

        val webView = WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (if (info.type == "YOUTUBE") 250 else 352).dpToPx(context)
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            
            val embedUrl = when (info.type) {
                "YOUTUBE" -> "https://www.youtube.com/embed/${info.id}?autoplay=1&modestbranding=1"
                "SPOTIFY" -> "https://open.spotify.com/embed/${info.category}/${info.id}?utm_source=generator"
                else -> ""
            }
            
            loadUrl(embedUrl)
        }
        
        root.addView(webView)
    }

    private fun loadMetadata(info: MediaInfo, imageView: ImageView, titleTv: TextView) {
        val cached = metadataCache[info.url]
        if (cached != null) {
            updateUi(info, cached, imageView, titleTv)
            return
        }

        if (info.type == "YOUTUBE") {
            imageView.load("https://img.youtube.com/vi/${info.id}/hqdefault.jpg")
        }

        scope.launch {
            val metadata = withContext(Dispatchers.IO) {
                try {
                    val oEmbedUrl = when (info.type) {
                        "YOUTUBE" -> "https://www.youtube.com/oembed?url=${info.url}&format=json"
                        "SPOTIFY" -> "https://open.spotify.com/oembed?url=${info.url}"
                        else -> null
                    }
                    if (oEmbedUrl != null) {
                        val json = URL(oEmbedUrl).readText()
                        gson.fromJson(json, EmbedMetadata::class.java)
                    } else null
                } catch (e: Exception) { null }
            }

            if (metadata != null) {
                metadataCache[info.url] = metadata
                updateUi(info, metadata, imageView, titleTv)
            }
        }
    }

    private fun updateUi(info: MediaInfo, metadata: EmbedMetadata, imageView: ImageView, titleTv: TextView) {
        metadata.title?.let { titleTv.text = it }
        metadata.thumbnail_url?.let {
            imageView.load(it) {
                crossfade(true)
            }
        }
    }

    private fun Int.dpToPx(context: android.content.Context): Int = 
        (this * context.resources.displayMetrics.density).toInt()
}
