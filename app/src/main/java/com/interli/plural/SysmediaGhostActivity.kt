package com.interli.plural

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SysmediaGhostActivity : BaseActivity() {

    private lateinit var posts: MutableList<SysmediaPost>
    private lateinit var people: MutableList<Person>
    private lateinit var ghostAccounts: List<GhostAccount>
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GhostAdapter

    data class GhostAccount(val id: String, val postCount: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sysmedia_ghost)
        ColorHelper.applySettings(this)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBar)
        toolbar.setNavigationOnClickListener { finish() }
        
        val textColor = ColorHelper.getTextColor(this)
        findViewById<View>(R.id.ghostRoot).setBackgroundColor(ColorHelper.getBgColor(this))
        toolbar.setTitleTextColor(textColor)
        toolbar.setNavigationIconTint(textColor)
        findViewById<TextView>(R.id.tvDescription).setTextColor(textColor)

        recyclerView = findViewById(R.id.rvGhostAccounts)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        loadData()
        scanForGhosts()
    }

    private fun loadData() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val postsJson = sharedPref.getString("sysmedia_posts", "[]")
        posts = Gson().fromJson(postsJson, object : TypeToken<MutableList<SysmediaPost>>() {}.type) ?: mutableListOf()
        people = MemberHelper.loadAllPeople(this)
    }

    private fun saveData() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        sharedPref.edit {
            putString("sysmedia_posts", Gson().toJson(posts))
        }
        MemberHelper.savePeople(this, people)
    }

    private fun scanForGhosts() {
        val activeIds = people.filter { !it.isArchived }.map { it.id }.toSet()
        val ghosts = posts.groupBy { it.senderId }
            .filter { it.key !in activeIds }
            .map { GhostAccount(it.key, it.value.size) }
            .sortedByDescending { it.postCount }
        
        ghostAccounts = ghosts
        adapter = GhostAdapter(ghostAccounts)
        recyclerView.adapter = adapter
        
        if (ghosts.isEmpty()) {
            Toast.makeText(this, "No orphaned posts found.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restoreAccount(ghost: GhostAccount) {
        val input = EditText(this).apply { hint = getString(R.string.hint_new_account_name) }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_restore_title)
            .setView(input)
            .setPositiveButton(R.string.action_add) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val newPerson = Person(id = ghost.id, name = name, isSysmediaOnly = true)
                    newPerson.sysmediaProfile = SysmediaProfile()
                    people.add(newPerson)
                    saveData()
                    scanForGhosts()
                    Toast.makeText(this, R.string.restore_success, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }

    private fun mergePosts(ghost: GhostAccount) {
        val mergePeople = people.filter { !it.isArchived }
        val names = mergePeople.map { it.name }.toTypedArray()
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_merge_title)
            .setItems(names) { _, which ->
                val targetPerson = mergePeople[which]
                posts.filter { it.senderId == ghost.id }.forEach {
                    it.senderId = targetPerson.id
                }
                saveData()
                scanForGhosts()
                Toast.makeText(this, R.string.merge_success, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }

    private fun showPostPreview(ghost: GhostAccount) {
        val ghostPosts = posts.filter { it.senderId == ghost.id }
            .sortedByDescending { it.timestamp }
        
        val items = ghostPosts.map { 
            val date = java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it.timestamp))
            "[$date] ${it.content.take(100)}${if (it.content.length > 100) "..." else ""}"
        }.toTypedArray()

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.posts_count_format, ghost.postCount))
            .setItems(items) { _, which ->
                val post = ghostPosts[which]
                val detailDialog = AlertDialog.Builder(this)
                    .setTitle("Post Content")
                    .setMessage(post.content)
                    .setPositiveButton(R.string.close, null)
                    .create()
                detailDialog.show()
                ColorHelper.styleSupportAlertDialog(detailDialog, this)
            }
            .setPositiveButton(R.string.close, null)
            .create()
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }

    private inner class GhostAdapter(private val items: List<GhostAccount>) :
        RecyclerView.Adapter<GhostAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvGhostId: TextView = view.findViewById(R.id.tvGhostId)
            val tvPostCount: TextView = view.findViewById(R.id.tvPostCount)
            val btnMerge: Button = view.findViewById(R.id.btnMerge)
            val btnRestore: Button = view.findViewById(R.id.btnRestore)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ghost_account, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val ghost = items[position]
            holder.tvGhostId.text = getString(R.string.label_ghost_id, ghost.id)
            holder.tvPostCount.text = getString(R.string.posts_count_format, ghost.postCount)
            
            val textColor = ColorHelper.getTextColor(this@SysmediaGhostActivity)
            val btnColor = ColorHelper.getBtnColor(this@SysmediaGhostActivity)
            val bgColor = ColorHelper.getBgColor(this@SysmediaGhostActivity)
            
            val itemView = holder.itemView
            if (itemView is com.google.android.material.card.MaterialCardView) {
                itemView.setCardBackgroundColor(bgColor)
                itemView.strokeColor = textColor and 0x33FFFFFF
                itemView.strokeWidth = 2
            }

            holder.tvGhostId.setTextColor(textColor)
            holder.tvPostCount.setTextColor(textColor)
            holder.btnMerge.setTextColor(btnColor)
            holder.btnRestore.setBackgroundColor(btnColor)
            holder.btnRestore.setTextColor(ColorHelper.getBtnTextColor(this@SysmediaGhostActivity))

            holder.btnRestore.setOnClickListener { restoreAccount(ghost) }
            holder.btnMerge.setOnClickListener { mergePosts(ghost) }
            holder.tvPostCount.setOnClickListener { showPostPreview(ghost) }
            holder.itemView.setOnClickListener { showPostPreview(ghost) }
        }

        override fun getItemCount() = items.size
    }
}
