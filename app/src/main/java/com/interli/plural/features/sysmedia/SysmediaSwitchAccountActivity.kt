package com.interli.plural.features.sysmedia

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.gson.Gson
import com.interli.plural.core.BaseActivity
import com.interli.plural.core.ColorHelper
import com.interli.plural.features.member.MemberHelper
import com.interli.plural.Person
import com.interli.plural.R
import java.util.*

class SysmediaSwitchAccountActivity : BaseActivity() {
    private lateinit var people: MutableList<Person>
    private var filteredPeople: MutableList<Person> = mutableListOf()
    private lateinit var adapter: AccountAdapter
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sysmedia_switch_account)
        loadData()

        filteredPeople = people.filter { !it.isArchived }.toMutableList()

        val rv = findViewById<RecyclerView>(R.id.rvAccounts)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = AccountAdapter(filteredPeople)
        rv.adapter = adapter

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) return false
                val etSearch = findViewById<android.widget.EditText>(R.id.etSearch)
                if (etSearch.text.isNotEmpty()) return false
                Collections.swap(filteredPeople, fromPos, toPos)
                adapter.notifyItemMoved(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                val etSearch = findViewById<android.widget.EditText>(R.id.etSearch)
                if (etSearch.text.isEmpty()) {
                      val archived = people.filter { it.isArchived }
                    people.clear()
                    people.addAll(filteredPeople)
                    people.addAll(archived)
                    saveData()
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(rv)

        val etSearch = findViewById<android.widget.EditText>(R.id.etSearch)
        val tvHint = findViewById<TextView>(R.id.tvHint)
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().lowercase()
                filteredPeople.clear()
                if (query.isEmpty()) {
                    tvHint.visibility = View.VISIBLE
                    filteredPeople.addAll(people.filter { !it.isArchived })
                } else {
                    tvHint.visibility = View.GONE
                    filteredPeople.addAll(people.filter {
                        !it.isArchived && (
                                it.name.lowercase().contains(query) ||
                                        it.sysmediaProfile?.handle?.lowercase()?.contains(query) == true ||
                                        it.sysmediaProfile?.displayName?.lowercase()?.contains(query) == true
                                )
                    })
                }
                adapter.notifyDataSetChanged()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        setupToolbar()
        ColorHelper.applySettings(this)
        val textColor = ColorHelper.getTextColor(this)
        findViewById<View>(R.id.topAppBar).setBackgroundColor(ColorHelper.getBgColor(this))
        etSearch.setTextColor(textColor)
        etSearch.setHintTextColor(textColor and 0x88FFFFFF.toInt())
        tvHint.setTextColor(textColor and 0x88FFFFFF.toInt())
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

    private inner class AccountAdapter(private var items: List<Person>) :
        RecyclerView.Adapter<AccountAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivAvatar: ImageView = view.findViewById(R.id.ivAvatar)
            val tvName: TextView = view.findViewById(R.id.tvName)
            val tvHandle: TextView = view.findViewById(R.id.tvHandle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sysmedia_account, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val person = items[position]
            val profile = person.sysmediaProfile
            val textColor = ColorHelper.getTextColor(this@SysmediaSwitchAccountActivity)

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

            holder.itemView.setOnClickListener {
                setResult(RESULT_OK, android.content.Intent().putExtra("selected_id", person.id))
                finish()
            }
        }

        override fun getItemCount() = items.size
    }
}