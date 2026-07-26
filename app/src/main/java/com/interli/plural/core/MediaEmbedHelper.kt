package com.interli.plural.core

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
    private val YOUTUBE_PATTERN = Pattern.compile("(?:https?://)?(?:www\\.)?(?:youtube\\.com/\\S*v=|youtu\\.be/)([a-zA-Z0-9_-]{11})", Pattern.CASE_INSENSITIVE)
    private val SPOTIFY_PATTERN = Pattern.compile("(?:https?://)?open\\.spotify\\.com/(track|album|playlist|artist)/([a-zA-Z0-9]+)", Pattern.CASE_INSENSITIVE)
    private val TUMBLR_PATTERN = Pattern.compile("(?:https?://)?(?:www\\.tumblr\\.com/([a-zA-Z0-9_-]+)/(\\d+)|([a-zA-Z0-9_-]+)\\.tumblr\\.com/post/(\\d+))", Pattern.CASE_INSENSITIVE)
    private val IMAGE_PATTERN = Pattern.compile("https?://\\S+\\.(?:gif|gifv|jpg|jpeg|png|webp|avif)", Pattern.CASE_INSENSITIVE)

    private val metadataCache = mutableMapOf<String, EmbedMetadata>()
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    data class MediaInfo(val type: String, val id: String, val url: String, val category: String? = null)
    data class EmbedMetadata(val title: String?, val thumbnail_url: String?)

    fun findMedia(content: String): List<MediaInfo> {
        val results = mutableListOf<MediaInfo>()
        val imgMatcher = IMAGE_PATTERN.matcher(content)
        while (imgMatcher.find()) {
            var url = imgMatcher.group(0)!!
            if (url.endsWith(".gifv", true)) url = url.substring(0, url.length - 1)
            results.add(MediaInfo("IMAGE", url, url))
        }
        val ytMatcher = YOUTUBE_PATTERN.matcher(content)
        while (ytMatcher.find()) results.add(MediaInfo("YOUTUBE", ytMatcher.group(1)!!, ytMatcher.group(0)!!))
        val spMatcher = SPOTIFY_PATTERN.matcher(content)
        while (spMatcher.find()) results.add(MediaInfo("SPOTIFY", spMatcher.group(2)!!, spMatcher.group(0)!!, spMatcher.group(1)))
        val tmMatcher = TUMBLR_PATTERN.matcher(content)
        while (tmMatcher.find()) {
            val user = tmMatcher.group(1) ?: tmMatcher.group(3)
            val id = tmMatcher.group(2) ?: tmMatcher.group(4)
            if (user != null && id != null) results.add(MediaInfo("TUMBLR", id, tmMatcher.group(0)!!, user))
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
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12.dpToPx(context); bottomMargin = 4.dpToPx(context) }
                radius = 12f * context.resources.displayMetrics.density
                cardElevation = 4f * context.resources.displayMetrics.density
                setCardBackgroundColor(ColorHelper.getBgColor(context))
            }

            val rootLayout = FrameLayout(context)
            val previewLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

            val thumbContainer = FrameLayout(context)
            val bigImage = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(-1, if (info.type == "IMAGE") -2 else 180.dpToPx(context))
                adjustViewBounds = true
                scaleType = if (info.type == "IMAGE") ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0x11000000)
            }
            thumbContainer.addView(bigImage)

            if (info.type != "IMAGE") {
                val playIcon = ImageView(context).apply {
                    setImageResource(android.R.drawable.ic_media_play)
                    layoutParams = FrameLayout.LayoutParams(64.dpToPx(context), 64.dpToPx(context)).apply { gravity = Gravity.CENTER }
                    alpha = 0.8f
                    setBackgroundResource(android.R.drawable.presence_online)
                }
                thumbContainer.addView(playIcon)
            } else {
                bigImage.load(info.url) { crossfade(true) }
            }
            previewLayout.addView(thumbContainer)

            if (info.type != "IMAGE") {
                val textPadding = 12.dpToPx(context)
                val infoLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(textPadding, textPadding, textPadding, textPadding)
                }
                val sourceTv = TextView(context).apply {
                    text = info.type; textSize = 11f; setTypeface(null, Typeface.BOLD); alpha = 0.6f
                    setTextColor(ColorHelper.getTextColor(context))
                }
                val titleTv = TextView(context).apply {
                    text = if (info.type == "TUMBLR") "Tumblr post van ${info.category}" else "Laden..."
                    textSize = 15f; setTypeface(null, Typeface.BOLD); maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                    setTextColor(ColorHelper.getTextColor(context))
                }
                infoLayout.addView(sourceTv)
                infoLayout.addView(titleTv)
                previewLayout.addView(infoLayout)
                loadMetadata(info, bigImage, titleTv)
            }

            card.setOnClickListener {
                if (info.type != "IMAGE") startPlayer(rootLayout, previewLayout, info)
            }

            card.addView(rootLayout.apply { addView(previewLayout) })
            container.addView(card)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun startPlayer(root: FrameLayout, preview: View, info: MediaInfo) {
        val context = root.context
        preview.visibility = View.GONE
        val webView = WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(-1, if (info.type == "YOUTUBE") 250.dpToPx(context) else 450.dpToPx(context))

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (info.type == "TUMBLR") {
                        val script = """
                            (function() {
                                var selectors = [
                                    '.tmblr-iframe--gdpr-banner', 
                                    '#qc-cmp2-container', 
                                    '.t-privacy-consent-wall',
                                    '.glass-container'
                                ];
                                selectors.forEach(function(selector) {
                                    var element = document.querySelector(selector);
                                    if (element) element.style.display = 'none';
                                });
                                document.body.style.overflow = 'auto';
                                document.documentElement.style.overflow = 'auto';
                            })();
                        """.trimIndent()
                        view?.evaluateJavascript(script, null)
                    }
                }
            }

            webChromeClient = WebChromeClient()

            val embedUrl = when (info.type) {
                "YOUTUBE" -> "https://www.youtube-nocookie.com/embed/${info.id}?autoplay=1&rel=0&enablejsapi=1&origin=https://www.youtube-nocookie.com"
                "SPOTIFY" -> "https://open.spotify.com/embed/${info.category}/${info.id}"
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
            titleTv.text = cached.title ?: titleTv.text
            cached.thumbnail_url?.let { url -> imageView.load(url) }
            return
        }

        if (info.type == "YOUTUBE") imageView.load("https://img.youtube.com/vi/${info.id}/hqdefault.jpg")

        scope.launch {
            val metadata = withContext(Dispatchers.IO) {
                try {
                    val oEmbedUrl = when (info.type) {
                        "YOUTUBE" -> "https://www.youtube.com/oembed?url=${info.url}&format=json"
                        "SPOTIFY" -> "https://open.spotify.com/oembed?url=${info.url}"
                        "TUMBLR" -> "https://www.tumblr.com/oembed/1.0?url=${info.url}"
                        else -> null
                    }
                    oEmbedUrl?.let { gson.fromJson(URL(it).readText(), EmbedMetadata::class.java) }
                } catch (e: Exception) { null }
            }
            metadata?.let {
                metadataCache[info.url] = it
                titleTv.text = it.title ?: titleTv.text
                it.thumbnail_url?.let { url -> imageView.load(url) }
            }
        }
    }

    private fun Int.dpToPx(context: Context): Int = (this * context.resources.displayMetrics.density).toInt()
}