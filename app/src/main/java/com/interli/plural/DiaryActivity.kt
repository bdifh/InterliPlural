package com.interli.plural

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.noties.markwon.Markwon
import java.text.SimpleDateFormat
import java.util.*

sealed class DiaryItem {
    data class BundleHeader(val bundle: NoteBundle) : DiaryItem()
    data class Note(val note: DiaryNote) : DiaryItem()
    data class Header(val title: String) : DiaryItem()
}

class DiaryActivity : BaseActivity() {

    private lateinit var allNotes: MutableList<DiaryNote>
    private lateinit var allBundles: MutableList<NoteBundle>
    private lateinit var people: List<Person>
    private var displayedItems: MutableList<DiaryItem> = mutableListOf()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DiaryAdapter
    private lateinit var markwon: Markwon
    private lateinit var allTodoLists: List<TodoList>
    private var currentTab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diary)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = true

        markwon = Markwon.create(this)
        loadNotes()

        recyclerView = findViewById(R.id.diaryRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = DiaryAdapter(displayedItems) { note ->
            val intent = Intent(this, EditNoteActivity::class.java)
            intent.putExtra("note_id", note.id)
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        val tabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabLayout)
        val btnAddNote = findViewById<Button>(R.id.btnAddNoteStyled)
        val btnAddBundle = findViewById<Button>(R.id.btnAddBundleDiary)
        val btnSend = findViewById<Button>(R.id.btnSendMessageDiary)

        tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                filterTabVisibility()
                filterNotes()
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })

        btnAddNote.setOnClickListener {
            val intent = Intent(this, EditNoteActivity::class.java)
            startActivity(intent)
        }

        btnAddBundle?.setOnClickListener {
            showAddBundleDialog()
        }

        btnSend.setOnClickListener {
            showSendMessageDialog()
        }

        setupNavigationDrawer()
        ColorHelper.applySettings(this)
        applyColors()

        val sp = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val frontEnabled = sp.getBoolean("module_fronting_enabled", true) && sp.getBoolean("sub_fronting_enabled", true)
        if (!frontEnabled) {
            if (tabLayout.tabCount > 1) {
                tabLayout.removeTabAt(1)
            }
            btnSend.visibility = View.GONE
        }
        filterTabVisibility()
        filterNotes()
    }

    private fun showAddBundleDialog() {
        val input = EditText(this).apply { hint = getString(R.string.hint_note_title) }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.action_new_bundle)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    allBundles.add(NoteBundle(name = name, manualOrder = allBundles.size + allNotes.size))
                    saveNotes()
                    filterNotes()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
        input.setTextColor(ColorHelper.getTextColor(this))
    }

    private fun showEditBundleDialog(bundle: NoteBundle) {
        val input = EditText(this).apply {
            hint = getString(R.string.hint_note_title)
            setText(bundle.name)
        }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.edit) + " " + getString(R.string.label_bundle))
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                bundle.name = input.text.toString().trim()
                saveNotes()
                filterNotes()
            }
            .setNeutralButton(R.string.delete) { _, _ ->
                allNotes.forEach {
                    if (it.bundleId == bundle.id) {
                        it.bundleId = null
                        it.bundleName = null
                    }
                }
                allBundles.remove(bundle)
                saveNotes()
                filterNotes()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
        input.setTextColor(ColorHelper.getTextColor(this))
    }

    private fun filterNotes() {
        displayedItems.clear()
        val filtered = if (currentTab == 0) {
            allNotes.filter { it.senderId == null && !it.isProfileOnly }
        } else {
            allNotes.filter { it.senderId != null && !it.isProfileOnly }
        }

        if (filtered.isEmpty() && (currentTab != 0 || allBundles.isEmpty())) {
            val emptyText = if (currentTab == 0) getString(R.string.no_notes) else getString(R.string.no_messages)
            displayedItems.add(DiaryItem.Header(emptyText))
        } else {
            if (currentTab == 1) {
                val grouped = filtered.sortedByDescending { it.timestamp }.groupBy { it.bundleName }
                grouped.forEach { (bundleTitle, bundleNotes) ->
                    if (bundleTitle != null) displayedItems.add(DiaryItem.Header(bundleTitle))
                    bundleNotes.forEach { displayedItems.add(DiaryItem.Note(it)) }
                }
            } else {
                val bundlesMap = allBundles.associateBy { it.id }
                val topLevelNotes = filtered.filter { it.bundleId == null || !bundlesMap.containsKey(it.bundleId) }

                val allContainers = mutableListOf<Any>()
                allContainers.addAll(allBundles)
                allContainers.addAll(topLevelNotes)

                val sortedContainers = allContainers.sortedWith(compareBy({
                    when (it) {
                        is NoteBundle -> it.manualOrder
                        is DiaryNote -> 999999
                        else -> 0
                    }
                }, {
                    if (it is DiaryNote) -it.timestamp else 0L
                }))

                sortedContainers.forEach { container ->
                    if (container is NoteBundle) {
                        displayedItems.add(DiaryItem.BundleHeader(container))
                        if (container.isExpanded) {
                            val childNotes = filtered.filter { it.bundleId == container.id }.sortedByDescending { it.timestamp }
                            childNotes.forEach { displayedItems.add(DiaryItem.Note(it)) }
                        }
                    } else if (container is DiaryNote) {
                        displayedItems.add(DiaryItem.Note(container))
                    }
                }
            }
        }
        adapter.updateData(displayedItems)
    }

    private fun showSendMessageDialog() {
        val intent = Intent(this, SendMessageActivity::class.java)
        startActivity(intent)
    }

    private fun applyColors() {
        val color = ColorHelper.getBtnColor(this)
        val btnTextColor = ColorHelper.getBtnTextColor(this)
        findViewById<Button>(R.id.btnAddBundleDiary)?.apply {
            setBackgroundColor(color)
            setTextColor(btnTextColor)
        }
    }

    private fun loadNotes() {
        people = MemberHelper.loadAllPeople(this)
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val json = sharedPref.getString("diary_notes", "[]")
        allNotes = Gson().fromJson(json, object : TypeToken<MutableList<DiaryNote>>() {}.type) ?: mutableListOf()

        val bundleJson = sharedPref.getString("diary_bundles", "[]")
        allBundles = Gson().fromJson(bundleJson, object : TypeToken<MutableList<NoteBundle>>() {}.type) ?: mutableListOf()

        val todoJson = sharedPref.getString("todo_lists", "[]")
        allTodoLists = Gson().fromJson(todoJson, object : TypeToken<List<TodoList>>() {}.type) ?: emptyList()

        var migrationDone = false
        allNotes.forEach { note ->
            if (note.bundleId == null && !note.bundleName.isNullOrEmpty()) {
                var bundle = allBundles.find { it.name == note.bundleName }
                if (bundle == null) {
                    bundle = NoteBundle(name = note.bundleName!!)
                    allBundles.add(bundle)
                }
                note.bundleId = bundle.id
                migrationDone = true
            }
        }
        if (migrationDone) saveNotes()
        allNotes.sortByDescending { it.timestamp }
    }

    private fun saveNotes() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        sharedPref.edit()
            .putString("diary_notes", Gson().toJson(allNotes))
            .putString("diary_bundles", Gson().toJson(allBundles))
            .apply()
    }

    override fun onResume() {
        super.onResume()
        ColorHelper.applySettings(this)
        applyColors()
        loadNotes()
        filterTabVisibility()
        filterNotes()
    }

    private fun filterTabVisibility() {
        val isNoteTab = currentTab == 0
        findViewById<View>(R.id.btnAddNoteStyled)?.visibility = if (isNoteTab) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnAddBundleDiary)?.visibility = if (isNoteTab) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnSendMessageDiary)?.visibility = if (currentTab == 1) View.VISIBLE else View.GONE
    }

    private inner class DiaryAdapter(
        private var items: List<DiaryItem>,
        private val onClick: (DiaryNote) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault())

        override fun getItemViewType(position: Int): Int = when (items[position]) {
            is DiaryItem.Header -> 0
            is DiaryItem.Note -> 1
            is DiaryItem.BundleHeader -> 2
        }

         inner class NoteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.noteTitle)
            val date: TextView = view.findViewById(R.id.noteDate)
            val sender: TextView = view.findViewById(R.id.noteSender)
            val recipients: TextView = view.findViewById(R.id.noteRecipients)
            val replyTo: TextView = view.findViewById(R.id.noteReplyTo)
            val preview: TextView = view.findViewById(R.id.notePreview)
            val linkedTodo: TextView = view.findViewById(R.id.linkedTodoLabel)
            val card: com.google.android.material.card.MaterialCardView = view as com.google.android.material.card.MaterialCardView
            val embedContainer: LinearLayout = view.findViewById(R.id.mediaEmbedContainerNoteItem)
        }

        inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view as TextView
        }

        inner class BundleViewHolder(val card: com.google.android.material.card.MaterialCardView) : RecyclerView.ViewHolder(card) {
            fun bind(bundle: NoteBundle) {
                card.layoutParams = (card.layoutParams as? ViewGroup.MarginLayoutParams ?: LinearLayout.LayoutParams(-1, -2)).apply {
                    setMargins(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 0)
                }
                card.radius = 8f * resources.displayMetrics.density
                card.setCardBackgroundColor(ColorHelper.getBtnColor(this@DiaryActivity))
                card.removeAllViews()

                val content = LinearLayout(this@DiaryActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(12.dpToPx(), 6.dpToPx(), 12.dpToPx(), 6.dpToPx())
                }

                val tvName = TextView(this@DiaryActivity).apply {
                    text = bundle.name
                    textSize = 16f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(ColorHelper.getBtnTextColor(this@DiaryActivity))
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                }
                content.addView(tvName)

                val btnExpand = ImageView(this@DiaryActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(24.dpToPx(), 24.dpToPx())
                    setImageResource(android.R.drawable.arrow_down_float)
                    rotation = if (bundle.isExpanded) 0f else -90f
                    setColorFilter(ColorHelper.getBtnTextColor(this@DiaryActivity))
                }
                content.addView(btnExpand)
                
                val btnEdit = ImageView(this@DiaryActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(24.dpToPx(), 24.dpToPx()).apply { marginStart = 8.dpToPx() }
                    setImageResource(android.R.drawable.ic_menu_edit)
                    setColorFilter(ColorHelper.getBtnTextColor(this@DiaryActivity))
                    alpha = 0.6f
                    setOnClickListener { showEditBundleDialog(bundle) }
                }
                content.addView(btnEdit)

                card.addView(content)

                card.setOnClickListener {
                    bundle.isExpanded = !bundle.isExpanded
                    saveNotes()
                    filterNotes()
                }
                card.setOnLongClickListener { showEditBundleDialog(bundle); true }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                0 -> HeaderViewHolder(TextView(parent.context).apply {
                    textSize = 18f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(16.dpToPx(parent.context), 24.dpToPx(parent.context), 16.dpToPx(parent.context), 8.dpToPx(parent.context))
                })
                2 -> BundleViewHolder(com.google.android.material.card.MaterialCardView(parent.context))
                else -> NoteViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_diary_note, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            val textColor = ColorHelper.getTextColor(this@DiaryActivity)
            val bgColor = ColorHelper.getBgColor(this@DiaryActivity)

            when (holder) {
                is HeaderViewHolder -> if (item is DiaryItem.Header) {
                    holder.title.text = item.title
                    holder.title.setTextColor(textColor)
                }
                is BundleViewHolder -> if (item is DiaryItem.BundleHeader) {
                    holder.bind(item.bundle)
                }
                is NoteViewHolder -> if (item is DiaryItem.Note) {
                    val note = item.note
                    (holder.card.layoutParams as? ViewGroup.MarginLayoutParams)?.marginStart = if (note.bundleId != null) 32.dpToPx() else 0
                    holder.card.setCardBackgroundColor(bgColor)
                    holder.card.strokeColor = textColor and 0x33FFFFFF
                    holder.title.text = note.title
                    holder.title.setTextColor(textColor)
                    holder.date.text = sdf.format(Date(note.timestamp))
                    holder.date.setTextColor(textColor)

                    // Extract "From" from content if it exists
                    val fromPrefixEn = "From: "
                    val fromPrefixNl = "Van: "
                    var displayContent = note.content
                    var senderText: String? = null

                    if (displayContent.startsWith(fromPrefixEn)) {
                        senderText = displayContent.substringBefore("\n\n")
                        displayContent = displayContent.substringAfter("\n\n")
                    } else if (displayContent.startsWith(fromPrefixNl)) {
                        senderText = displayContent.substringBefore("\n\n")
                        displayContent = displayContent.substringAfter("\n\n")
                    }

                    if (senderText != null) {
                        holder.sender.visibility = View.VISIBLE
                        holder.sender.text = senderText
                        holder.sender.setTextColor(textColor)
                        holder.sender.textSize = 12f
                    } else {
                        holder.sender.visibility = View.GONE
                    }

                    val recipientsLine = if (note.nextFronterRecipient != null) {
                        getString(R.string.message_to_placeholder, getString(R.string.next_fronter_format, note.nextFronterRecipient))
                    } else if (note.linkedMemberIds.isNotEmpty()) {
                        val names = note.linkedMemberIds.map { id -> people.find { it.id == id }?.name ?: id }
                        getString(R.string.message_to_placeholder, names.joinToString(", "))
                    } else {
                        null
                    }

                    if (recipientsLine != null) {
                        holder.recipients.visibility = View.VISIBLE
                        holder.recipients.text = recipientsLine
                        holder.recipients.setTextColor(textColor)
                        holder.recipients.textSize = 12f
                    } else {
                        holder.recipients.visibility = View.GONE
                    }

                    if (note.parentNoteId != null) {
                        val parent = allNotes.find { it.id == note.parentNoteId }
                        if (parent != null) {
                            holder.replyTo.visibility = View.VISIBLE
                            holder.replyTo.text = "${getString(R.string.label_reply_to)}: ${parent.title}"
                            holder.replyTo.setTextColor(textColor)
                            holder.replyTo.textSize = 12f
                        } else {
                            holder.replyTo.visibility = View.GONE
                        }
                    } else {
                        holder.replyTo.visibility = View.GONE
                    }

                    markwon.setMarkdown(holder.preview, displayContent.replace("\r\n", "\n").replace("\n", "  \n"))
                    holder.preview.setTextColor(textColor)

                    val todo = allTodoLists.find { it.id == note.linkedTodoListId }
                    if (todo != null) {
                        holder.linkedTodo.visibility = View.VISIBLE
                        holder.linkedTodo.text = "${getString(R.string.label_linked_todo)}: ${todo.title}"
                        holder.linkedTodo.setTextColor(textColor)
                    } else {
                        holder.linkedTodo.visibility = View.GONE
                    }

                    MediaEmbedHelper.addEmbedsToContainer(holder.embedContainer, note.content)
                    holder.itemView.setOnClickListener { onClick(note) }
                }
            }
        }

        override fun getItemCount() = items.size
        fun updateData(newItems: List<DiaryItem>) { items = newItems; notifyDataSetChanged() }
        private fun Int.dpToPx(c: android.content.Context = this@DiaryActivity): Int = (this * c.resources.displayMetrics.density).toInt()
    }
    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
