package com.interli.plural.features.sysmedia

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.core.BaseActivity
import com.interli.plural.core.ColorHelper
import com.interli.plural.features.diary.CreatePostActivity
import com.interli.plural.features.member.MemberHelper
import com.interli.plural.Person
import com.interli.plural.R
import com.interli.plural.SysmediaPost
import com.interli.plural.SysmediaProfile
import java.text.SimpleDateFormat
import java.util.*

class SysmediaScheduledPostsActivity : BaseActivity() {
    private lateinit var posts: MutableList<SysmediaPost>
    private lateinit var people: List<Person>
    private lateinit var activeMemberId: String
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ScheduledPostAdapter
    private val gson = Gson()
    private val sdf = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sysmedia_scheduled_posts)
        activeMemberId = intent.getStringExtra("active_member_id") ?: ""
        loadData()
        recyclerView = findViewById(R.id.rvScheduledPosts)
        recyclerView.layoutManager = LinearLayoutManager(this)
        updateList()
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBar)
        toolbar.setNavigationOnClickListener { finish() }
        ColorHelper.applySettings(this)
        toolbar.setBackgroundColor(ColorHelper.getBgColor(this))
        toolbar.setTitleTextColor(ColorHelper.getTextColor(this))
        toolbar.setNavigationIconTint(ColorHelper.getTextColor(this))
    }
    private fun loadData() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val postsJson = sharedPref.getString("sysmedia_posts", "[]")
        posts = gson.fromJson(postsJson, object : TypeToken<MutableList<SysmediaPost>>() {}.type) ?: mutableListOf()
        people = MemberHelper.loadAllPeople(this)
    }
    private fun saveData() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        sharedPref.edit {
            putString("sysmedia_posts", gson.toJson(posts))
        }
    }
    private fun updateList() {
        val now = System.currentTimeMillis()
        val scheduled = posts.filter { it.scheduledTime != null && it.scheduledTime!! > now }.sortedBy { it.scheduledTime }
        adapter = ScheduledPostAdapter(scheduled)
        recyclerView.adapter = adapter
    }
    override fun onResume() {
        super.onResume()
        loadData()
        updateList()
    }
    private inner class ScheduledPostAdapter(private val items: List<SysmediaPost>) :
        RecyclerView.Adapter<ScheduledPostAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivAvatar: ImageView = view.findViewById(R.id.ivAvatar)
            val tvName: TextView = view.findViewById(R.id.tvName)
            val tvHandle: TextView = view.findViewById(R.id.tvHandle)
            val tvContent: TextView = view.findViewById(R.id.tvContent)
            val tvTime: TextView = view.findViewById(R.id.tvTimeBottom)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sysmedia_post, parent, false)
            view.findViewById<View>(R.id.btnReply).parent.let { (it as View).visibility = View.GONE }
            return ViewHolder(view)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val post = items[position]
            val sender = people.find { it.id == post.senderId }
            val profile = sender?.sysmediaProfile
            val textColor = ColorHelper.getTextColor(this@SysmediaScheduledPostsActivity)
            holder.tvName.text = profile?.displayName ?: sender?.name ?: "Unknown"
            holder.tvName.setTextColor(textColor)
            val handle = profile?.handle ?: sender?.name?.replace(" ", "_")?.lowercase()?.replace(Regex("[^a-z0-9_]"), "") ?: sender?.manualId ?: "unknown"
            holder.tvHandle.text = "@$handle"
            holder.tvHandle.setTextColor(textColor and 0x88FFFFFF.toInt())
            holder.tvContent.text = post.content
            holder.tvContent.setTextColor(textColor)
            val dateStr = sdf.format(Date(post.scheduledTime!!))
            holder.tvTime.text = getString(R.string.scheduled_for_format, dateStr)
            holder.tvTime.setTextColor(textColor and 0x88FFFFFF.toInt())
            val avatarUri = profile?.profilePictureUri ?: sender?.profilePictureUri
            if (avatarUri != null) {
                holder.ivAvatar.load(avatarUri)
            } else {
                val color = if (sender?.profileColor == -6934396 || sender == null) ColorHelper.getBtnColor(this@SysmediaScheduledPostsActivity) else sender.profileColor
                holder.ivAvatar.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
                holder.ivAvatar.setImageResource(android.R.color.transparent)
            }
            holder.itemView.setOnClickListener {
                showPostOptions(post)
            }
        }
        private fun showPostOptions(post: SysmediaPost) {
            val options = arrayOf(getString(R.string.action_edit), getString(R.string.delete))
            AlertDialog.Builder(this@SysmediaScheduledPostsActivity)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            val intent = android.content.Intent(this@SysmediaScheduledPostsActivity, CreatePostActivity::class.java)
                            intent.putExtra("current_user_id", post.senderId)
                            intent.putExtra("edit_post_id", post.id)
                            startActivity(intent)
                        }
                        1 -> {
                            posts.remove(post)
                            saveData()
                            updateList()
                        }
                    }
                }.show()
        }
        override fun getItemCount() = items.size
    }
}
