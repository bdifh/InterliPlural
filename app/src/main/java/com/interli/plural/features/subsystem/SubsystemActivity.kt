package com.interli.plural.features.subsystem

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.R
import com.interli.plural.core.BaseActivity
import com.interli.plural.core.ColorHelper
import com.interli.plural.features.member.MemberHelper
import com.interli.plural.Person
import com.interli.plural.FrontSession

data class SubsystemMember(
    val id: String = java.util.UUID.randomUUID().toString(),
    var personId: String? = null,
    var name: String,
    var isFronting: Boolean = false,
    var profileColor: Int = -6934396
)

data class SubsystemGroup(
    val id: String = java.util.UUID.randomUUID().toString(),
    var name: String,
    val members: MutableList<SubsystemMember> = mutableListOf(),
    var isBodyFronting: Boolean = false
)

class SubsystemMemberAdapter(
    private val context: Context,
    private val members: List<SubsystemMember>,
    private val onMemberClick: (SubsystemMember) -> Unit
) : RecyclerView.Adapter<SubsystemMemberAdapter.MemberViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_person, parent, false)
        return MemberViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val subMember = members[position]
        val activity = context as? SubsystemActivity
        val linkedPerson = activity?.mainPeople?.find { it.id == subMember.personId || it.id == subMember.id }
        val displayName = linkedPerson?.name ?: subMember.name
        val displayColor = linkedPerson?.profileColor ?: subMember.profileColor
        val avatarUri = linkedPerson?.profilePictureUri

        val textColor = ColorHelper.getTextColor(context)
        val btnColor = ColorHelper.getBtnColor(context)

        holder.tvName.text = displayName
        holder.tvName.setTextColor(textColor)
        holder.card.setCardBackgroundColor(ColorHelper.getBgColor(context))
        holder.card.strokeColor = displayColor
        holder.smallImageCard.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
        holder.smallImageCard.strokeWidth = 0

        if (!avatarUri.isNullOrBlank()) {
            holder.profileImage.load(avatarUri) {
                crossfade(true)
                placeholder(android.R.drawable.ic_menu_gallery)
                error(android.R.drawable.ic_menu_report_image)
            }
        } else {
            holder.profileImage.load(android.R.drawable.ic_menu_gallery)
        }
        holder.itemView.setOnClickListener {
            val targetId = linkedPerson?.id ?: subMember.personId ?: subMember.id
            val intent = Intent(context, com.interli.plural.features.member.ProfileActivity::class.java)
            intent.putExtra("person_id", targetId)
            context.startActivity(intent)
        }

        holder.itemView.setOnLongClickListener {
            activity?.showMemberOptionsDialog(subMember)
            true
        }

        holder.btnFront.text = if (subMember.isFronting) context.getString(R.string.unfront_arrow) else context.getString(R.string.front_arrow)
        holder.btnFront.setOnClickListener { onMemberClick(subMember) }
        holder.btnFront.setBackgroundColor(if (subMember.isFronting) ColorHelper.getFrontColor(context) else btnColor)
        holder.btnFront.setTextColor(if (subMember.isFronting) textColor else ColorHelper.getBtnTextColor(context))
    }

    override fun getItemCount() = members.size

    class MemberViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val card: MaterialCardView = v.findViewById(R.id.personCard)
        val smallImageCard: MaterialCardView = v.findViewById(R.id.personSmallImageCard)
        val tvName: TextView = v.findViewById(R.id.nameText)
        val btnFront: Button = v.findViewById(R.id.frontButton)
        val profileImage: ImageView = v.findViewById(R.id.personSmallImage)
    }
}

class SubsystemGroupAdapter(
    private val context: Context,
    private val groups: List<SubsystemGroup>,
    private val onMemberToggle: () -> Unit,
    private val onAddMemberClick: (SubsystemGroup) -> Unit
) : RecyclerView.Adapter<SubsystemGroupAdapter.GroupViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_subsystem_group, parent, false)
        return GroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = groups[position]
        val textColor = ColorHelper.getTextColor(context)

        holder.groupCard.setCardBackgroundColor(ColorHelper.getBgColor(context))
        holder.tvName.text = group.name
        holder.tvName.setTextColor(textColor)
        holder.btnToggleGroupBodyFront.text = if (group.isBodyFronting) context.getString(R.string.unfront_arrow) else context.getString(R.string.front_arrow)
        holder.btnToggleGroupBodyFront.setBackgroundColor(if (group.isBodyFronting) ColorHelper.getFrontColor(context) else ColorHelper.getBtnColor(context))
        holder.btnToggleGroupBodyFront.setTextColor(if (group.isBodyFronting) textColor else ColorHelper.getBtnTextColor(context))
        holder.btnToggleGroupBodyFront.setOnClickListener {
            (context as? SubsystemActivity)?.toggleGroupBodyFront(group)
        }

        val fronters = group.members.filter { it.isFronting }
        val fronterNames = fronters.map { subMember ->
            val linkedPerson = (context as? SubsystemActivity)?.mainPeople?.find { it.id == subMember.personId || it.id == subMember.id }
            linkedPerson?.name ?: subMember.name
        }
        holder.tvFrontStatus.text = if (fronterNames.isEmpty()) context.getString(R.string.nobody_fronting_group) else fronterNames.joinToString(", ")
        holder.tvFrontStatus.setTextColor(textColor)
        holder.frontCard.setCardBackgroundColor(ColorHelper.getFrontColor(context))

        holder.btnAddMember.setTextColor(ColorHelper.getBtnColor(context))
        holder.btnGroupStats.setTextColor(ColorHelper.getBtnColor(context))
        holder.btnAddMember.setOnClickListener { onAddMemberClick(group) }

        holder.btnGroupStats.setOnClickListener {
            val intent = Intent(context, com.interli.plural.core.StatisticsActivity::class.java)
            val memberIds = group.members.map { it.personId ?: it.id }
            intent.putStringArrayListExtra("filter_member_ids", ArrayList(memberIds))
            intent.putExtra("session_key", "subsystem_sessions")
            intent.putExtra("is_subsystem", true)
            context.startActivity(intent)
        }

        val activity = context as? SubsystemActivity
        holder.groupCard.setOnLongClickListener {
            activity?.showGroupOptionsDialog(group)
            true
        }
        holder.tvName.setOnLongClickListener {
            activity?.showGroupOptionsDialog(group)
            true
        }

        holder.rvMembers.layoutManager = LinearLayoutManager(context)
        holder.rvMembers.adapter = SubsystemMemberAdapter(context, group.members) { member ->
            activity?.toggleMemberFront(member)
        }
    }

    override fun getItemCount() = groups.size

    class GroupViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val groupCard: MaterialCardView = v.findViewById(R.id.groupCard)
        val tvName: TextView = v.findViewById(R.id.tvGroupName)
        val btnToggleGroupBodyFront: Button = v.findViewById(R.id.btnToggleGroupBodyFront)
        val tvFrontStatus: TextView = v.findViewById(R.id.tvGroupFrontStatus)
        val frontCard: MaterialCardView = v.findViewById(R.id.frontBarCard)
        val btnGroupStats: Button = v.findViewById(R.id.btnGroupStats)
        val btnAddMember: Button = v.findViewById(R.id.btnAddMemberToGroup)
        val rvMembers: RecyclerView = v.findViewById(R.id.rvGroupMembers)
    }
}

class SubsystemActivity : BaseActivity() {
    val groups = mutableListOf<SubsystemGroup>()
    var mainPeople = mutableListOf<Person>()
    private var subsystemSessions = mutableListOf<FrontSession>()
    private lateinit var groupAdapter: SubsystemGroupAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subsystems)
        setupNavigationDrawer()

        loadData()

        val rv = findViewById<RecyclerView>(R.id.rvSubsystemGroups)
        rv.layoutManager = LinearLayoutManager(this)

        groupAdapter = SubsystemGroupAdapter(this, groups, {
            saveData()
            groupAdapter.notifyDataSetChanged()
        }, { group ->
            showAddMemberDialog(group)
        })
        rv.adapter = groupAdapter

        findViewById<Button>(R.id.btnAddGroup).setOnClickListener { showAddGroupDialog() }
    }

    fun loadData() {
        mainPeople = MemberHelper.loadAllPeople(this)

        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)

        val jsonGroups = sharedPref.getString("subsystem_data", "[]")
        val typeGroups = object : TypeToken<MutableList<SubsystemGroup>>() {}.type
        groups.clear()
        groups.addAll(Gson().fromJson(jsonGroups, typeGroups) ?: mutableListOf())

        var hasChanges = false
        groups.forEach { group ->
            group.members.forEach { member ->
                val linked = mainPeople.find { it.id == member.personId || it.id == member.id }
                if (linked != null) {
                    if (member.personId == null) {
                        member.personId = linked.id
                        hasChanges = true
                    }
                    if (member.name != linked.name) {
                        member.name = linked.name
                        hasChanges = true
                    }
                    if (member.profileColor != linked.profileColor) {
                        member.profileColor = linked.profileColor
                        hasChanges = true
                    }
                }
            }
        }
        if (hasChanges) {
            saveData()
        }

        val jsonSessions = sharedPref.getString("subsystem_sessions", "[]")
        val typeSessions = object : TypeToken<MutableList<FrontSession>>() {}.type
        subsystemSessions.clear()
        subsystemSessions.addAll(Gson().fromJson(jsonSessions, typeSessions) ?: mutableListOf())
    }

    private fun saveData() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val gson = Gson()

        val jsonGroups = gson.toJson(groups)
        val jsonSessions = gson.toJson(subsystemSessions)

        sharedPref.edit()
            .putString("subsystem_data", jsonGroups)
            .putString("subsystem_sessions", jsonSessions)
            .apply()

        com.interli.plural.widgets.SubsystemFronterWidget.sendRefreshBroadcast(this)
    }

    private fun showAddGroupDialog() {
        val input = EditText(this)
        val container = createDialogContainer(input)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.add_subsystem_group)
            .setView(container)
            .setPositiveButton(R.string.action_add) { _, _ ->
                if (input.text.isNotEmpty()) {
                    groups.add(SubsystemGroup(name = input.text.toString()))
                    saveData()
                    groupAdapter.notifyDataSetChanged()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.show()
        ColorHelper.styleAlertDialog(dialog, this)
    }

    fun showGroupOptionsDialog(group: SubsystemGroup) {
        val options = arrayOf(getString(R.string.edit), getString(R.string.delete))
        val dialog = AlertDialog.Builder(this)
            .setTitle(group.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameGroupDialog(group)
                    1 -> showDeleteGroupConfirmationDialog(group)
                }
            }
            .create()
        dialog.show()
        ColorHelper.styleAlertDialog(dialog, this)
    }

    private fun showRenameGroupDialog(group: SubsystemGroup) {
        val input = EditText(this)
        input.setText(group.name)
        val container = createDialogContainer(input)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_edit_group_title)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                if (input.text.isNotEmpty()) {
                    group.name = input.text.toString()
                    saveData()
                    groupAdapter.notifyDataSetChanged()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.show()
        ColorHelper.styleAlertDialog(dialog, this)
    }

    private fun showDeleteGroupConfirmationDialog(group: SubsystemGroup) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_group_title)
            .setPositiveButton(R.string.delete) { _, _ ->
                groups.remove(group)
                saveData()
                groupAdapter.notifyDataSetChanged()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.show()
        ColorHelper.styleAlertDialog(dialog, this)
    }

    fun toggleMemberFront(member: SubsystemMember) {
        member.isFronting = !member.isFronting
        val now = System.currentTimeMillis()
        val linkedPerson = mainPeople.find { it.id == member.personId }
        val displayName = linkedPerson?.name ?: member.name

        if (member.isFronting) {
            subsystemSessions.add(
                FrontSession(
                    personName = displayName,
                    startTime = now,
                    personId = member.personId ?: member.id
                )
            )
        } else {
            subsystemSessions.filter {
                (it.personId == (member.personId ?: member.id)) && it.endTime == null
            }.forEach { it.endTime = now }
        }
        saveData()
        groupAdapter.notifyDataSetChanged()
    }

    fun toggleGroupBodyFront(group: SubsystemGroup) {
        group.isBodyFronting = !group.isBodyFronting
        saveData()

        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val sessionsJson = sharedPref.getString("sessions_list", "[]") ?: "[]"
        val typeSessions = object : TypeToken<MutableList<FrontSession>>() {}.type
        val sessions: MutableList<FrontSession> = try {
            Gson().fromJson(sessionsJson, typeSessions)
        } catch (e: Exception) {
            mutableListOf()
        }

        val subPersonId = "subsystem_${group.id}"
        if (group.isBodyFronting) {
            sessions.add(
                FrontSession(
                    personName = group.name,
                    startTime = System.currentTimeMillis(),
                    personId = subPersonId
                )
            )
        } else {
            sessions.filter { (it.personId == subPersonId || (it.personId == null && it.personName == group.name)) && it.endTime == null }
                .forEach { it.endTime = System.currentTimeMillis() }
        }
        sharedPref.edit().putString("sessions_list", Gson().toJson(sessions)).apply()

        com.interli.plural.widgets.CurrentFronterWidget.sendRefreshBroadcast(this)
        groupAdapter.notifyDataSetChanged()
    }

    private fun showAddMemberDialog(group: SubsystemGroup) {
        val input = EditText(this)
        val container = createDialogContainer(input)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.add_subsystem_member)
            .setView(container)
            .setPositiveButton(R.string.action_add) { _, _ ->
                if (input.text.isNotEmpty()) {
                    group.members.add(SubsystemMember(name = input.text.toString()))
                    saveData()
                    groupAdapter.notifyDataSetChanged()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.show()
        ColorHelper.styleAlertDialog(dialog, this)
    }

    fun showMemberOptionsDialog(subMember: SubsystemMember) {
        val options = mutableListOf<String>()
        if (subMember.personId == null) {
            options.add(getString(R.string.add_to_frontpage))
        }

        options.add(getString(R.string.delete_from_subsystem))

        val dialog = AlertDialog.Builder(this)
            .setTitle(subMember.name)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    getString(R.string.add_to_frontpage) -> promoteToMain(subMember)
                    getString(R.string.delete_from_subsystem) -> removeMemberFromSubsystem(
                        subMember
                    )
                }
            }
            .create()
        dialog.show()
        ColorHelper.styleAlertDialog(dialog, this)
    }

    private fun promoteToMain(subMember: SubsystemMember) {
        val newPerson = Person(name = subMember.name, profileColor = subMember.profileColor)
        mainPeople.add(newPerson)
        MemberHelper.savePeople(this, mainPeople)

        subMember.personId = newPerson.id
        saveData()
        groupAdapter.notifyDataSetChanged()
        Toast.makeText(this, getString(R.string.entry_saved), Toast.LENGTH_SHORT).show()
    }

    private fun removeMemberFromSubsystem(subMember: SubsystemMember) {
        for (group in groups) {
            if (group.members.remove(subMember)) {
                saveData()
                groupAdapter.notifyDataSetChanged()
                Toast.makeText(this, getString(R.string.member_deleted), Toast.LENGTH_SHORT)
                    .show()
                break
            }
        }
    }

    private fun createDialogContainer(input: EditText): LinearLayout {
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        input.layoutParams = lp
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            addView(input)
        }
    }

    override fun onResume() {
        super.onResume()
        loadData()
        if (::groupAdapter.isInitialized) {
            groupAdapter.notifyDataSetChanged()
        }
    }
}