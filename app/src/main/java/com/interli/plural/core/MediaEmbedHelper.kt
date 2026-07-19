package com.interli.plural.core

import android.R
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.load
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private val TUMBLR_PATTERN = Pattern.compile(
        "(?:https?://)?(?:(?:www\\.)?tumblr\\.com/([a-zA-Z0-9_-]+)/(\\d+)|([a-zA-Z0-9_-]+)\\.tumblr\\.com/post/(\\d+))",
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
        val tmMatcher = TUMBLR_PATTERN.matcher(content)
        while (tmMatcher.find()) {
            val username = tmMatcher.group(1) ?: tmMatcher.group(3)
            val id = tmMatcher.group(2) ?: tmMatcher.group(4)
            if (username != null && id != null) {
                results.add(MediaInfo("TUMBLR", id, tmMatcher.group(0)!!, username))
            }
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
                setImageResource(R.drawable.ic_media_play)
                layoutParams = FrameLayout.LayoutParams(
                    (64 * context.resources.displayMetrics.density).toInt(),
                    (64 * context.resources.displayMetrics.density).toInt()
                ).apply {
                    gravity = Gravity.CENTER
                }
                alpha = 0.9f
                setPadding(16, 16, 16, 16)
                setBackgroundResource(R.drawable.presence_online)
                background?.setTint(0xCC000000.toInt())
            }
            thumbnailContainer.addView(playIcon)
            previewLayout.addView(thumbnailContainer)
            val textLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16.dpToPx(context), 12.dpToPx(context), 16.dpToPx(context), 12.dpToPx(context))
            }
            val titleTv = TextView(context).apply {
                text = when (info.type) {
                    "YOUTUBE" -> "YouTube Video"
                    "SPOTIFY" -> "Spotify ${info.category?.lowercase()?.replaceFirstChar { it.uppercase() }}"
                    "TUMBLR" -> "Tumblr Post van ${info.category}"
                    else -> "Media"
                }
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ColorHelper.getTextColor(context))
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }
            val sourceTv = TextView(context).apply {
                text = when (info.type) {
                    "YOUTUBE" -> "YouTube"
                    "SPOTIFY" -> "Spotify"
                    "TUMBLR" -> "Tumblr"
                    else -> ""
                }
                textSize = 12f
                setTextColor(when (info.type) {
                    "YOUTUBE" -> 0xFFFF0000.toInt()
                    "SPOTIFY" -> 0xFF1DB954.toInt()
                    "TUMBLR" -> 0xFF35465c.toInt() // Tumblr Blauw
                    else -> 0xFF888888.toInt()
                })
                setTypeface(null, Typeface.BOLD)
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

            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                mediaPlaybackRequiresUserGesture = false

                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                if (info.type == "YOUTUBE") {
                    userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36"
                }
            }

            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()

            val embedUrl = when (info.type) {
                "YOUTUBE" -> "https://www.youtube-nocookie.com/embed/${info.id}?autoplay=1&rel=0&showinfo=0&enablejsapi=1&origin=https://www.youtube-nocookie.com"
                "SPOTIFY" -> "https://open.spotify.com/embed/${info.category}/${info.id}?utm_source=generator"
                "TUMBLR" -> "https://www.tumblr.com/embed/post/${info.category}/${info.id}"
                else -> ""
            }

            val headers = mutableMapOf<String, String>()
            if (info.type == "YOUTUBE") {
                headers["Referer"] = "https://www.youtube-nocookie.com"
            }

            loadUrl(embedUrl, headers)
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
                        "TUMBLR" -> "https://www.tumblr.com/oembed/1.0?url=${info.url}"
                        else -> null
                    }
                    if (oEmbedUrl != null) {
                        val json = URL(oEmbedUrl).readText()
                        gson.fromJson(json, EmbedMetadata::class.java)
                    } else null
                } catch (e: Exception) {
                    null
                }
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
    private fun Int.dpToPx(context: Context): Int =
        (this * context.resources.displayMetrics.density).toInt()
}