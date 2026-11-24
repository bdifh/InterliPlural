package com.interli.plural.features.member

import android.content.Context
import android.graphics.Color
import android.view.*
import android.widget.*
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.core.ColorHelper
import com.interli.plural.Group
import com.interli.plural.Person
import com.interli.plural.R
import kotlin.math.min

sealed class MainItem {
    data class GroupHeader(val group: Group, val depth: Int) : MainItem()
    data class PersonRow(val person: Person, val originalIndex: Int, val depth: Int) : MainItem()
}
class PersonAdapter(
    private val context: Context,
    private val groups: MutableList<Group>,
    private val people: MutableList<Person>,
    private val onFrontClicked: (Person) -> Unit,
    private val onProfileClicked: (Person, String) -> Unit,
    private val onGroupToggled: (Group) -> Unit,
    private val onGroupLongClicked: (Group) -> Unit,
    private val onFrontLongClicked: ((Person) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private data class AdapterColors(
        val background: Int,
        val text: Int,
        val button: Int,
        val buttonText: Int
    )

    private var cachedColors: AdapterColors? = null

    private fun readAdapterColors(): AdapterColors {
        val preferences =
            context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        val theme = ColorHelper.getEffectiveTheme(context)

        fun parseColor(value: String?, fallback: String): Int {
            return try {
                (value ?: fallback).toColorInt()
            } catch (_: Exception) {
                fallback.toColorInt()
            }
        }

        return AdapterColors(
            background = parseColor(
                theme?.bgColor ?: preferences.getString("bg_color", null),
                "#FFFDF0"
            ),
            text = parseColor(
                theme?.textColor ?: preferences.getString("text_color", null),
                "#1A1811"
            ),
            button = parseColor(
                theme?.btnColor ?: preferences.getString("btn_color", null),
                "#7D4EBA"
            ),
            buttonText = parseColor(
                theme?.btnTextColor ?: preferences.getString("btn_text_color", null),
                "#D2B8F5"
            )
        )
    }
    private var items: List<MainItem> = listOf()
    private var filterQuery: String = ""
    init {
        updateItems()
    }
    fun filter(query: String) {
        filterQuery = query
        updateItems(refreshColors = false)
    }
    fun updateItems(refreshColors: Boolean = true) {
        if (refreshColors || cachedColors == null) {
            cachedColors = readAdapterColors()
        }
        val newList = mutableListOf<MainItem>()
        val filteredPeople = if (filterQuery.isBlank()) {
            people.filter { !it.isSysmediaOnly && !it.isArchived }
        } else {
            people.filter { it.name.contains(filterQuery, ignoreCase = true) && !it.isSysmediaOnly && !it.isArchived }
        }
        val unassigned = filteredPeople.asSequence()
            .filter { it.safeGroupIds.isEmpty() }
            .sortedBy { it.name.lowercase() }
            .toList()
        unassigned.forEach { person ->
            newList.add(MainItem.PersonRow(person, people.indexOf(person), 0))
        }
        val rootGroups = groups.asSequence()
            .filter { it.parentGroupId == null }
            .sortedBy { it.name.lowercase() }
            .toList()
        rootGroups.forEach { group ->
            buildTree(group, newList, 0, filteredPeople)
        }
        items = newList
        notifyDataSetChanged()
    }
    private fun buildTree(group: Group, list: MutableList<MainItem>, depth: Int, filteredPeople: List<Person>) {
        val groupMembers = filteredPeople.filter { it.safeGroupIds.contains(group.id) }
        val subGroups = groups.filter { it.parentGroupId == group.id }
        if (groupMembers.isNotEmpty() || subGroups.any { hasMembersRecursive(it, filteredPeople) }) {
            list.add(MainItem.GroupHeader(group, depth))
            if (group.isExpanded || filterQuery.isNotEmpty()) {
                groupMembers.asSequence()
                    .sortedBy { it.name.lowercase() }
                    .forEach { person ->
                        list.add(MainItem.PersonRow(person, people.indexOf(person), depth + 1))
                    }
                subGroups.asSequence()
                    .sortedBy { it.name.lowercase() }
                    .forEach { subGroup ->
                        buildTree(subGroup, list, depth + 1, filteredPeople)
                    }
            }
        }
    }
    private fun hasMembersRecursive(group: Group, filteredPeople: List<Person>): Boolean {
        if (filteredPeople.any { it.safeGroupIds.contains(group.id) }) return true
        return groups.filter { it.parentGroupId == group.id }.any { hasMembersRecursive(it, filteredPeople) }
    }
    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is MainItem.GroupHeader -> 0
            is MainItem.PersonRow -> 1
        }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == 0) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_group, parent, false)
            GroupViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_person, parent, false)
            PersonViewHolder(view)
        }
    }
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val context = holder.itemView.context
        val item = items[position]
        val colors = cachedColors
            ?: readAdapterColors().also { cachedColors = it }
        if ((holder is GroupViewHolder) && (item is MainItem.GroupHeader)) {
            val group = item.group
            holder.nameText.text = group.name
            holder.nameText.setTextColor(colors.text)
            holder.expandIcon.rotation = if (group.isExpanded) 0f else -90f
            holder.expandIcon.setColorFilter(colors.text)
            holder.card.setCardBackgroundColor(colors.background)
            holder.card.strokeWidth = 0 
            val params = holder.card.layoutParams as ViewGroup.MarginLayoutParams
            params.marginStart = (item.depth * 24 * context.resources.displayMetrics.density).toInt() + (8 * context.resources.displayMetrics.density).toInt()
            holder.card.layoutParams = params
            holder.itemView.setOnClickListener {
                onGroupToggled(group)
                updateItems()
            }
            holder.itemView.setOnLongClickListener {
                onGroupLongClicked(group)
                true
            }
        } else if (holder is PersonViewHolder && item is MainItem.PersonRow) {
            val person = item.person
            val originalIndex = item.originalIndex
            holder.nameText.text = person.name
            holder.nameText.setTextColor(colors.text)
            holder.card.strokeColor = person.profileColor
            holder.card.setCardBackgroundColor(colors.background)
            val baseBtnColor = colors.button
            val btnTextColor = colors.buttonText
            if (person.isFront) {
                holder.frontButton.text = context.getString(R.string.unfront_arrow)
                holder.frontButton.setBackgroundColor(btnTextColor)
                holder.frontButton.setTextColor(baseBtnColor)
            } else {
                holder.frontButton.text = context.getString(R.string.front_arrow)
                holder.frontButton.setBackgroundColor(baseBtnColor)
                holder.frontButton.setTextColor(btnTextColor)
            }

            holder.notificationDot.visibility = if (person.isFront && !person.frontMessage.isNullOrBlank() && !person.messageRead) View.VISIBLE else View.GONE
            val params = holder.card.layoutParams as ViewGroup.MarginLayoutParams
            params.marginStart = (item.depth * 24 * context.resources.displayMetrics.density).toInt() + (8 * context.resources.displayMetrics.density).toInt()
            holder.card.layoutParams = params
            holder.smallImageCard.setCardBackgroundColor(colors.background)
            val avatarUri = person.profilePictureUri
            if (avatarUri != null) {
                holder.smallImage.load(avatarUri) {
                    placeholder(android.R.drawable.ic_menu_gallery)
                    error(android.R.drawable.ic_menu_gallery)
                }
            } else {
                holder.smallImage.load(android.R.drawable.ic_menu_gallery)
            }
            holder.frontButton.setOnClickListener {
                onFrontClicked(person)
                notifyDataSetChanged()
            }
            holder.frontButton.setOnLongClickListener {
                onFrontLongClicked?.invoke(person)
                true
            }
            holder.nameText.setOnClickListener {
                onProfileClicked(person, person.id)
            }
        }
    }
    class PersonViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: com.google.android.material.card.MaterialCardView = view.findViewById(R.id.personCard)
        val smallImageCard: com.google.android.material.card.MaterialCardView = view.findViewById(R.id.personSmallImageCard)
        val nameText: TextView = view.findViewById(R.id.nameText)
        val frontButton: Button = view.findViewById(R.id.frontButton)
        val smallImage: ImageView = view.findViewById(R.id.personSmallImage)
        val notificationDot: View = view.findViewById(R.id.notificationDot)
    }
    class GroupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: com.google.android.material.card.MaterialCardView = view.findViewById(R.id.groupCard)
        val nameText: TextView = view.findViewById(R.id.groupNameText)
        val expandIcon: ImageView = view.findViewById(R.id.groupExpandIcon)
    }
}
