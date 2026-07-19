package com.interli.plural.features.mood

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.core.BaseActivity
import com.interli.plural.core.ColorHelper
import com.interli.plural.features.mood.MoodActivity
import com.interli.plural.features.mood.MoodChartView
import com.interli.plural.R
import java.util.*

class MemberMoodStatsActivity : BaseActivity() {
    private val gson = Gson()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_member_mood_stats)
        ColorHelper.applySettings(this)
        val personId = intent.getStringExtra("person_id") ?: return
        val personName = intent.getStringExtra("person_name") ?: ""
        findViewById<TextView>(R.id.memberMoodStatsTitle).text = getString(R.string.member_mood_stats, personName)
        setupNavigationDrawer()
        renderStats(personId)
    }
    private fun renderStats(personId: String) {
        val allEntries = loadEntries()
        val filtered = allEntries.filter { it.memberIds.contains(personId) }
        if (filtered.isEmpty()) {
            findViewById<View>(R.id.cardMemberMoodChart).visibility = View.GONE
            return
        }
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        val recentFiltered = filtered.filter { it.timestamp >= thirtyDaysAgo }
        findViewById<MoodChartView>(R.id.memberMoodChart).setData(filtered, MoodChartView.Mode.TIMELINE)
        renderMoodCounts(recentFiltered)
        renderActivityInfluence(recentFiltered)
    }
    private fun renderMoodCounts(entries: List<MoodActivity.MoodEntry>) {
        val container = findViewById<LinearLayout>(R.id.containerMemberMoodCounts)
        container.removeAllViews()
        val moodKeys = listOf("mood_awful", "mood_bad", "mood_meh", "mood_good", "mood_rad")
        val counts = entries.groupingBy { it.moodLabel }.eachCount()
        val consolidatedCounts = mutableMapOf<String, Int>()
        counts.forEach { (label, count) ->
            val key = if (moodKeys.contains(label)) label else {
                moodKeys.find { key ->
                    val resId = resources.getIdentifier(key, "string", packageName)
                    resId != 0 && getString(resId) == label
                } ?: label
            }
            consolidatedCounts[key] = (consolidatedCounts[key] ?: 0) + count
        }
        val sorted = moodKeys.reversed().map { it to (consolidatedCounts[it] ?: 0) }
        val total = entries.size.toFloat()
        val textColor = ColorHelper.getTextColor(this)
        sorted.forEach { (key, count) ->
            if (count == 0) return@forEach
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, 16.dpToPx())
            }
            val moodIndex = moodKeys.indexOf(key)
            val displayName = (moodIndex + 1).toString()
            val percentage = (count / total * 100).toInt()
            val labelTv = TextView(this).apply {
                text = getString(R.string.stats_score_count, displayName, count, percentage)
                textSize = 14f
                setTextColor(textColor)
            }
            row.addView(labelTv)
            val bar = View(this).apply {
                val weight = count / total
                layoutParams = LinearLayout.LayoutParams(0, 8.dpToPx(), weight).apply {
                    topMargin = 4.dpToPx()
                }
                setBackgroundColor(ColorHelper.getMoodColor(this@MemberMoodStatsActivity, key))
            }
            val barContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(bar)
                addView(View(this@MemberMoodStatsActivity).apply { 
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f - (count / total))
                })
            }
            row.addView(barContainer)
            container.addView(row)
        }
    }
    private fun renderActivityInfluence(entries: List<MoodActivity.MoodEntry>) {
        val container = findViewById<LinearLayout>(R.id.containerMemberActivityInfluence)
        container.removeAllViews()
        val moodKeys = listOf("mood_awful", "mood_bad", "mood_meh", "mood_good", "mood_rad")
        val moodLabels = moodKeys.map { key ->
            val resId = resources.getIdentifier(key, "string", packageName)
            if (resId != 0) getString(resId) else key
        }
        val activityScores = mutableMapOf<String, MutableList<Int>>()
        entries.forEach { entry ->
            var score = moodKeys.indexOf(entry.moodLabel)
            if (score == -1) score = moodLabels.indexOf(entry.moodLabel)
            if (score != -1) {
                entry.activities.forEach { activity ->
                    activityScores.getOrPut(activity) { mutableListOf() }.add(score)
                }
            }
        }
        if (activityScores.isEmpty()) return
        val activityStats = activityScores.map { (activity, scores) ->
            Triple(activity, scores.average(), scores.size)
        }.sortedByDescending { it.third }
        val maxCount = activityStats.maxOf { it.third }.toFloat()
        val textColor = ColorHelper.getTextColor(this)
        activityStats.forEach { (activity, avg, count) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, 16.dpToPx())
            }
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val nameTv = TextView(this).apply {
                text = getString(R.string.stats_activity_count, activity, count)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setTextColor(textColor)
                textSize = 14f
            }
            header.addView(nameTv)
            val scoreTv = TextView(this).apply {
                text = getString(R.string.stats_score_value, String.format(Locale.getDefault(), "%.1f", avg + 1))
                textSize = 12f
                setTextColor(textColor)
                alpha = 0.7f
            }
            header.addView(scoreTv)
            row.addView(header)
            val bar = View(this).apply {
                val weight = count / maxCount
                layoutParams = LinearLayout.LayoutParams(0, 8.dpToPx(), weight).apply {
                    topMargin = 4.dpToPx()
                }
                setBackgroundColor(ColorHelper.getMoodColorByScore(this@MemberMoodStatsActivity, avg.toFloat()))
            }
            val barContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(bar)
                addView(View(this@MemberMoodStatsActivity).apply { 
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f - (count / maxCount))
                })
            }
            row.addView(barContainer)
            container.addView(row)
        }
    }
    private fun loadEntries(): List<MoodActivity.MoodEntry> {
        val prefs = getSharedPreferences("my_app", MODE_PRIVATE)
        val json = prefs.getString("mood_entries", "[]") ?: "[]"
        val type = object : TypeToken<List<MoodActivity.MoodEntry>>() {}.type
        return try { gson.fromJson(json, type) } catch (_: Exception) { emptyList() }
    }
    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
