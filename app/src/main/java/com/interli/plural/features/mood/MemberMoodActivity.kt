package com.interli.plural.features.mood

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.imageLoader
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.core.BaseActivity
import com.interli.plural.core.ColorHelper
import com.interli.plural.DiaryNote
import com.interli.plural.core.MediaEmbedHelper
import com.interli.plural.R
import com.interli.plural.TodoList
import java.text.SimpleDateFormat
import java.util.*

class MemberMoodActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_member_mood)

        ColorHelper.applySettings(this)
        val personId = intent.getStringExtra("person_id") ?: return
        val personName = intent.getStringExtra("person_name") ?: ""
        findViewById<TextView>(R.id.memberMoodTitle).text = getString(R.string.mood_for_member, personName)

        findViewById<Button>(R.id.btnViewMemberMoodStats).setOnClickListener {
            val intent = android.content.Intent(this, MemberMoodStatsActivity::class.java)
            intent.putExtra("person_id", personId)
            intent.putExtra("person_name", personName)
            startActivity(intent)
        }

        setupNavigationDrawer()

        val prefs = getSharedPreferences("my_app", MODE_PRIVATE)
        val gson = Gson()

        val moodJson = prefs.getString("mood_entries", "[]") ?: "[]"
        val rawList: List<Map<String, Any?>> = try {
            gson.fromJson(moodJson, object : TypeToken<List<Map<String, Any?>>>() {}.type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        val moods = rawList.map { map ->
            MoodActivity.MoodEntry(
                id = (map["id"] as? String) ?: UUID.randomUUID().toString(),
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
                moodEmoji = (map["moodEmoji"] as? String) ?: "😊",
                moodRotation = (map["moodRotation"] as? Number)?.toFloat() ?: 0f,
                moodLabel = (map["moodLabel"] as? String) ?: "mood_meh",
                moodColor = (map["moodColor"] as? Number)?.toInt() ?: 0xFFCCCCCC.toInt(),
                memberIds = (map["memberIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                activities = (map["activities"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                note = (map["note"] as? String) ?: "",
                linkedNoteId = map["linkedNoteId"] as? String,
                linkedTodoId = map["linkedTodoId"] as? String,
                imageUri = map["imageUri"] as? String
            )
        }

        val container = findViewById<LinearLayout>(R.id.memberMoodContainer)
        container.removeAllViews()

        val filtered = moods.filter { it.memberIds.contains(personId) }.sortedByDescending { it.timestamp }
        val textColor = ColorHelper.getTextColor(this)
        val bgColor = ColorHelper.getBgColor(this)
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

        if (filtered.isEmpty()) {
            container.addView(TextView(this).apply {
                text = getString(R.string.no_mood_for_member)
                setPadding(0, 50.dpToPx(), 0, 0)
                gravity = Gravity.CENTER
                setTextColor(textColor)
                alpha = 0.5f
            })
            return
        }

        filtered.forEach { entry ->
            val card = com.google.android.material.card.MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, 16.dpToPx())
                }
                radius = 12f * resources.displayMetrics.density
                setCardBackgroundColor(bgColor)
                strokeWidth = 1
                strokeColor = (textColor and 0x33FFFFFF) or 0x33000000
            }

            val content = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
                gravity = Gravity.CENTER_VERTICAL
            }

            val moodIndicator = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(40.dpToPx(), 40.dpToPx())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ColorHelper.getMoodColor(this@MemberMoodActivity, entry.moodLabel))
                }
                addView(TextView(this@MemberMoodActivity).apply {
                    text = entry.moodEmoji
                    rotation = entry.moodRotation
                    textSize = 20f
                    gravity = Gravity.CENTER
                })
            }
            content.addView(moodIndicator)

            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 16.dpToPx() }
            }

            info.addView(TextView(this).apply {
                val moodKeys = listOf("mood_awful", "mood_bad", "mood_meh", "mood_good", "mood_rad")
                val moodIndex = moodKeys.indexOf(entry.moodLabel)
                val scoreLabel = if (moodIndex != -1) (moodIndex + 1).toString() else "?"
                text = sdf.format(Date(entry.timestamp)) + " • " + entry.moodLabel.replace("mood_", "").capitalize()
                textSize = 12f
                setTextColor(textColor)
                alpha = 0.7f
            })

            if (entry.activities.isNotEmpty()) {
                info.addView(TextView(this).apply {
                    text = entry.activities.joinToString(", ")
                    textSize = 14f
                    setTextColor(textColor)
                })
            }

            if (entry.note.isNotEmpty()) {
                info.addView(TextView(this).apply {
                    text = entry.note
                    textSize = 13f
                    setTextColor(textColor)
                    alpha = 0.8f
                    setPadding(0, 4.dpToPx(), 0, 0)
                })
            }

            if (!entry.imageUri.isNullOrEmpty()) {
                val iv = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = 8.dpToPx()
                    }
                    adjustViewBounds = true
                    maxHeight = 250.dpToPx()
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                imageLoader.enqueue(coil.request.ImageRequest.Builder(this)
                    .data(entry.imageUri).target(iv).build())
                info.addView(iv)
            }

            content.addView(info)
            card.addView(content)
            container.addView(card)
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}