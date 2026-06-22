package com.interli.plural

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SysmediaMemberListActivity : BaseActivity() {

    private lateinit var people: MutableList<Person>
    private lateinit var activeMemberId: String
    private lateinit var adapter: MemberAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sysmedia_member_list)

        activeMemberId = intent.getStringExtra("active_member_id") ?: ""
        loadData()

        val rv = findViewById<RecyclerView>(R.id.rvMembers)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = MemberAdapter(people)
        rv.adapter = adapter

        setupToolbar()
        
        val bgColor = ColorHelper.getBgColor(this)
        val textColor = ColorHelper.getTextColor(this)
        
        findViewById<View>(R.id.main).setBackgroundColor(bgColor)
        
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBar)
        toolbar.setBackgroundColor(bgColor)
        toolbar.setNavigationIconTint(textColor)
        toolbar.setTitleTextColor(textColor)

        ColorHelper.applySettings(this)
    }

    private fun loadData() {
        people = MemberHelper.loadAllPeople(this)
    }

    private fun saveData() {
        MemberHelper.savePeople(this, people)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBar)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private inner class MemberAdapter(private val items: List<Person>) :
        RecyclerView.Adapter<MemberAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivAvatar: ImageView = view.findViewById(R.id.ivAvatar)
            val tvName: TextView = view.findViewById(R.id.tvName)
            val tvHandle: TextView = view.findViewById(R.id.tvHandle)
            val btnFollow: Button = view.findViewById(R.id.btnFollow)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sysmedia_member_list, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val person = items[position]
            val profile = person.sysmediaProfile
            val textColor = ColorHelper.getTextColor(this@SysmediaMemberListActivity)
            
            holder.tvName.text = profile?.displayName ?: person.name
            holder.tvName.setTextColor(textColor)
            
            val handle = profile?.handle ?: person.name.replace(" ", "_").lowercase().replace(Regex("[^a-z0-9_]"), "")
            holder.tvHandle.text = "@$handle"
            holder.tvHandle.setTextColor(textColor and 0x88FFFFFF.toInt())
            
            val avatarUri = profile?.profilePictureUri ?: person.profilePictureUri
            val userColor = ColorHelper.getUserColor(person.id, person.profileColor)
            val colorDrawable = android.graphics.drawable.ColorDrawable(userColor)

            if (avatarUri != null && avatarUri.isNotEmpty()) {
                holder.ivAvatar.load(avatarUri) {
                    placeholder(colorDrawable)
                    error(colorDrawable)
                }
            } else {
                holder.ivAvatar.setImageDrawable(colorDrawable)
            }

            if (person.id == activeMemberId) {
                holder.btnFollow.visibility = View.GONE
            } else {
                holder.btnFollow.visibility = View.VISIBLE
                val activePerson = people.find { it.id == activeMemberId }
                val isFollowing = activePerson?.sysmediaProfile?.followingIds?.contains(person.id) == true
                
                holder.btnFollow.text = if (isFollowing) getString(R.string.action_unfollow) else getString(R.string.action_follow)
                
                val baseBtnColor = ColorHelper.getBtnColor(this@SysmediaMemberListActivity)
                val btnTextColor = ColorHelper.getBtnTextColor(this@SysmediaMemberListActivity)
                
                if (isFollowing) {
                    val hsv = FloatArray(3)
                    android.graphics.Color.colorToHSV(baseBtnColor, hsv)
                    hsv[2] = 1.0f 
                    hsv[1] = hsv[1] * 0.7f
                    holder.btnFollow.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.HSVToColor(hsv))
                    holder.btnFollow.setTextColor(android.graphics.Color.WHITE)
                } else {
                    holder.btnFollow.backgroundTintList = android.content.res.ColorStateList.valueOf(baseBtnColor)
                    holder.btnFollow.setTextColor(btnTextColor)
                }
                
                holder.btnFollow.setOnClickListener {
                    activePerson?.sysmediaProfile?.let { activeProfile ->
                        if (isFollowing) {
                            activeProfile.followingIds.remove(person.id)
                        } else {
                            activeProfile.followingIds.add(person.id)
                        }
                        saveData()
                        notifyItemChanged(position)
                    }
                }
            }

            holder.itemView.setOnClickListener {
                val intent = android.content.Intent(this@SysmediaMemberListActivity, SysmediaProfileActivity::class.java)
                intent.putExtra("profile_user_id", person.id)
                intent.putExtra("active_member_id", activeMemberId)
                startActivity(intent)
            }
        }

        override fun getItemCount() = items.size
    }
}
