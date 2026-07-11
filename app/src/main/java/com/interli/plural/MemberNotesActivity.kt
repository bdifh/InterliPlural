package com.interli.plural

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import com.google.android.material.tabs.TabLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import java.text.SimpleDateFormat
import java.util.*

class MemberNotesActivity : BaseActivity() {

    private var currentTab = 0
    private lateinit var personId: String
    private lateinit var personName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_member_notes)

        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = true

        ColorHelper.applySettings(this)

        personId = intent.getStringExtra("person_id") ?: return
        personName = intent.getStringExtra("person_name") ?: ""

        findViewById<TextView>(R.id.memberNameTitle).text = getString(R.string.notes_for_member, personName)

        try {
            findViewById<Button>(R.id.btnAddNoteMemberStyled)
                .setOnClickListener {
                    val intent = android.content.Intent(this, EditNoteActivity::class.java)
                    intent.putExtra("pre_link_member_id", personId)
                    startActivity(intent)
                }
        } catch (_: Exception) {}

        findViewById<Button>(R.id.btnSendMessage).setOnClickListener {
            showSendMessageDialog(personId)
        }

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val btnAddNote = findViewById<Button>(R.id.btnAddNoteMemberStyled)
        val btnSend = findViewById<Button>(R.id.btnSendMessage)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                btnAddNote.visibility = if (currentTab == 0) View.VISIBLE else View.GONE
                btnSend.visibility = if (currentTab == 1) View.VISIBLE else View.GONE
                loadContent()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        btnAddNote.visibility = if (currentTab == 0) View.VISIBLE else View.GONE
        btnSend.visibility = if (currentTab == 1) View.VISIBLE else View.GONE

        setupNavigationDrawer()
        loadContent()
    }

    override fun onResume() {
        super.onResume()
        loadContent()
    }

    private fun loadContent() {
        if (currentTab == 0) {
            loadMemberNotes(personId)
        } else {
            loadMessages(personId)
        }
    }

    private fun loadMemberNotes(personId: String) {
        val prefs = getSharedPreferences("my_app", MODE_PRIVATE)
        val notesJson = prefs.getString("diary_notes", "[]") ?: "[]"
        val gson = Gson()
        val type = object : TypeToken<List<DiaryNote>>() {}.type
        val notes: List<DiaryNote> = try { gson.fromJson(notesJson, type) } catch (_: Exception) { emptyList() }

        val container = findViewById<LinearLayout>(R.id.notesContainer)
        container.removeAllViews()

        val filtered = notes.filter { it.linkedMemberIds?.contains(personId) == true && it.senderId == null }
        
        displayNotes(filtered, container)
    }

    private fun loadMessages(personId: String) {
        val prefs = getSharedPreferences("my_app", MODE_PRIVATE)
        val notesJson = prefs.getString("diary_notes", "[]") ?: "[]"
        val gson = Gson()
        val type = object : TypeToken<List<DiaryNote>>() {}.type
        val allNotes: List<DiaryNote> = try { gson.fromJson(notesJson, type) } catch (_: Exception) { emptyList() }

        val container = findViewById<LinearLayout>(R.id.notesContainer)
        container.removeAllViews()

        val filtered = allNotes.filter { 
            it.senderId != null && (it.senderId == personId || it.linkedMemberIds?.contains(personId) == true)
        }
        
        displayNotes(filtered, container)
    }

    private fun displayNotes(notes: List<DiaryNote>, container: LinearLayout) {
        if (notes.isEmpty()) {
            val message = TextView(this).apply {
                text = if (currentTab == 0) getString(R.string.no_notes) else getString(R.string.no_messages)
                setTextColor(ColorHelper.getTextColor(this@MemberNotesActivity))
                setPadding(24,24,24,24)
            }
            container.addView(message)
            return
        }

        val markwon = Markwon.builder(this)
            .usePlugin(TablePlugin.create(this))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(CoilImagesPlugin.create(this))
            .usePlugin(LinkifyPlugin.create())
            .build()
        val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault())

        val inflater = layoutInflater
        val bgColor = ColorHelper.getBgColor(this)
        val textColor = ColorHelper.getTextColor(this)

        val grouped = notes.sortedByDescending { it.timestamp }.groupBy { it.bundleName }

        grouped.forEach { (bundleTitle, bundleNotes) ->
            if (bundleTitle != null) {
                val header = TextView(this).apply {
                    text = bundleTitle
                    textSize = 16f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(textColor)
                    val py = (12 * resources.displayMetrics.density).toInt()
                    setPadding(py, py, py, py / 2)
                }
                container.addView(header)
            }

            bundleNotes.forEach { note ->
                val view = inflater.inflate(R.layout.item_diary_note, container, false)
                val card = view as? com.google.android.material.card.MaterialCardView
                card?.setCardBackgroundColor(bgColor)

                val titleView = view.findViewById<TextView>(R.id.noteTitle)
                val dateView = view.findViewById<TextView>(R.id.noteDate)
                val recipientView = view.findViewById<TextView>(R.id.noteRecipients)
                val previewView = view.findViewById<TextView>(R.id.notePreview)
                val embedContainer = view.findViewById<LinearLayout>(R.id.mediaEmbedContainerNoteItem)

                titleView.setTextColor(textColor)
                dateView.setTextColor(textColor)
                recipientView.setTextColor(textColor)
                previewView.setTextColor(textColor)

                titleView.text = note.title ?: getString(R.string.unnamed_note)
                dateView.text = sdf.format(Date(note.timestamp))

                if (note.nextFronterRecipient != null) {
                    recipientView.visibility = View.VISIBLE
                    recipientView.text = getString(R.string.next_fronter_format, note.nextFronterRecipient)
                } else {
                    recipientView.visibility = View.GONE
                }

                val contentProcessed = (note.content ?: "").replace("\r\n", "\n").replace("\n", "  \n")
                markwon.setMarkdown(previewView, contentProcessed)
                previewView.movementMethod = android.text.method.LinkMovementMethod.getInstance()

                MediaEmbedHelper.addEmbedsToContainer(embedContainer, note.content ?: "")

                view.isClickable = true
                view.isFocusable = true

                view.setOnClickListener {
                    val intent = android.content.Intent(this, EditNoteActivity::class.java)
                    intent.putExtra("note_id", note.id)
                    startActivity(intent)
                }

                container.addView(view)
            }
        }
    }

    private fun showSendMessageDialog(targetId: String) {
        val intent = android.content.Intent(this, SendMessageActivity::class.java)
        intent.putExtra("target_id", targetId)
        startActivity(intent)
    }
}
