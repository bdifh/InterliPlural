package com.interli.plural

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

class MoodTimelineActivity : BaseActivity() {

    private val gson = Gson()
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private var adapter: MoodEntryAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mood_timeline)

        ColorHelper.applySettings(this)

        recyclerView = findViewById(R.id.rvMoodEntries)
        emptyView = findViewById(R.id.tvEmptyMoods)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        setupNavigationDrawer()
        findViewById<View>(R.id.fabAddMood).setOnClickListener {
            val intent = Intent(this, MoodActivity::class.java)
            startActivity(intent)
        }

        renderList()
    }

    override fun onResume() {
        super.onResume()
        renderList()
    }

    private fun renderList() {
        val progressBar = android.widget.ProgressBar(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            )
        }
        (findViewById<View>(android.R.id.content) as? android.widget.FrameLayout)?.addView(progressBar)

        Thread {
            val entries = loadEntries().sortedByDescending { it.timestamp }
            
            val prefs = getSharedPreferences("my_app", MODE_PRIVATE)
            val peopleJson = prefs.getString("people_list", "[]") ?: "[]"
            val allPeople: List<Person> = gson.fromJson(peopleJson, object : TypeToken<List<Person>>() {}.type)
            val excludedIds = allPeople.filter { it.excludeFromStats || it.isArchived }.map { it.id }.toSet()

            val filteredEntries = entries.filter { entry ->
                entry.memberIds.isEmpty() || entry.memberIds.any { !excludedIds.contains(it) }
            }

            runOnUiThread {
                progressBar.visibility = View.GONE
                (progressBar.parent as? android.view.ViewGroup)?.removeView(progressBar)

                if (filteredEntries.isEmpty()) {
                    recyclerView.visibility = View.GONE
                    emptyView.visibility = View.VISIBLE
                    return@runOnUiThread
                } else {
                    recyclerView.visibility = View.VISIBLE
                    emptyView.visibility = View.GONE
                }

                if (adapter == null) {
                    adapter = MoodEntryAdapter(
                        context = this,
                        entries = filteredEntries,
                        people = allPeople,
                        onEdit = { entry ->
                            val intent = Intent(this, MoodActivity::class.java)
                            intent.putExtra("edit_entry_id", entry.id)
                            startActivity(intent)
                        },
                        onDelete = { entry ->
                            val currentEntries = loadEntries()
                            currentEntries.removeAll { it.id == entry.id }
                            persistEntries(currentEntries)
                            renderList()
                        }
                    )
                    recyclerView.adapter = adapter
                } else {
                    adapter?.updateData(entries)
                }
            }
        }.start()
    }

    private fun loadEntries(): MutableList<MoodActivity.MoodEntry> {
        val prefs = getSharedPreferences("my_app", MODE_PRIVATE)
        val json = prefs.getString("mood_entries", "[]") ?: "[]"
        
        val rawList: List<Map<String, Any?>> = try {
            gson.fromJson(json, object : TypeToken<List<Map<String, Any?>>>() {}.type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        return rawList.map { map ->
            try {
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
                    linkedTodoId = map["linkedTodoId"] as? String
                )
            } catch (e: Exception) {
                MoodActivity.MoodEntry(timestamp = 0L, moodEmoji = "❓", moodRotation = 0f, moodLabel = "error", moodColor = 0)
            }
        }.toMutableList()
    }

    private fun persistEntries(list: List<MoodActivity.MoodEntry>) {
        val prefs = getSharedPreferences("my_app", MODE_PRIVATE)
        prefs.edit().putString("mood_entries", gson.toJson(list)).apply()
    }
}
