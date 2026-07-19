package com.interli.plural.core

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.content.edit
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.core.BaseActivity
import com.interli.plural.core.ColorHelper
import com.interli.plural.CustomField
import com.interli.plural.features.member.MemberHelper
import com.interli.plural.R
import java.util.Collections

class CustomFieldsActivity : BaseActivity() {
    private val customFields = mutableListOf<CustomField>()
    private lateinit var fieldsAdapter: FieldsAdapter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_fields)
        val sharedPref = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val fieldsJson = sharedPref.getString("custom_fields", "[]") ?: "[]"
        val type = object : TypeToken<MutableList<CustomField>>() {}.type
        val loadedFields: List<CustomField> = Gson().fromJson(fieldsJson, type)
        customFields.addAll(loadedFields)
        val recyclerView = findViewById<RecyclerView>(R.id.customFieldsRecyclerView)
        fieldsAdapter = FieldsAdapter(customFields)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = fieldsAdapter
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                Collections.swap(customFields, fromPos, toPos)
                fieldsAdapter.notifyItemMoved(fromPos, toPos)
                return true
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)
        findViewById<Button>(R.id.btnAddField).setOnClickListener {
            customFields.add(CustomField(name = "", template = ""))
            fieldsAdapter.notifyItemInserted(customFields.size - 1)
            recyclerView.scrollToPosition(customFields.size - 1)
        }
        findViewById<Button>(R.id.btnSaveCustomFields).setOnClickListener {
            customFields.forEach { it.getUniqueId() }
            sharedPref.edit {
                putString("custom_fields", Gson().toJson(customFields))
            }
            val allPeople = MemberHelper.loadAllPeople(this)
            MemberHelper.migrateFields(this, allPeople)
            MemberHelper.savePeople(this, allPeople)
            Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
            finish()
        }
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBar).setNavigationOnClickListener {
            finish()
        }
        updateUIColors()
    }
    private fun updateUIColors() {
        val textColor = ColorHelper.getTextColor(this)
        val bgColor = ColorHelper.getBgColor(this)
        val btnColor = ColorHelper.getBtnColor(this)
        val btnTextColor = ColorHelper.getBtnTextColor(this)
        findViewById<View>(android.R.id.content).setBackgroundColor(bgColor)
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBar).apply {
            setBackgroundColor(bgColor)
            setNavigationIconTint(textColor)
        }
        findViewById<Button>(R.id.btnAddField).setTextColor(btnColor)
        findViewById<Button>(R.id.btnSaveCustomFields).apply {
            setBackgroundColor(btnColor)
            setTextColor(btnTextColor)
        }
    }
    inner class FieldsAdapter(private val fields: MutableList<CustomField>) : RecyclerView.Adapter<FieldsAdapter.FieldViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FieldViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_custom_field_config, parent, false)
            return FieldViewHolder(v)
        }
        override fun onBindViewHolder(holder: FieldViewHolder, position: Int) {
            val field = fields[position]
            holder.editName.setText(field.name)
            holder.editTemplate.setText(field.template)
            holder.nameWatcher?.let { holder.editName.removeTextChangedListener(it) }
            holder.templateWatcher?.let { holder.editTemplate.removeTextChangedListener(it) }
            holder.nameWatcher = object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) { field.name = s.toString() }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            }
            holder.editName.addTextChangedListener(holder.nameWatcher)
            holder.templateWatcher = object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) { field.template = s.toString() }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            }
            holder.editTemplate.addTextChangedListener(holder.templateWatcher)
            @Suppress("ClickableViewAccessibility")
            holder.editTemplate.setOnTouchListener { v, event ->
                if (v.hasFocus()) {
                    v.parent.requestDisallowInterceptTouchEvent(true)
                    if ((event.action and android.view.MotionEvent.ACTION_MASK) == android.view.MotionEvent.ACTION_UP) {
                        v.parent.requestDisallowInterceptTouchEvent(false)
                    }
                }
                false
            }
            holder.btnDelete.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    fields.removeAt(pos)
                    notifyItemRemoved(pos)
                }
            }
            val textColor = ColorHelper.getTextColor(this@CustomFieldsActivity)
            val frontColor = ColorHelper.getFrontColor(this@CustomFieldsActivity)
            holder.card.setCardBackgroundColor(frontColor)
            holder.editName.setTextColor(textColor)
            holder.editTemplate.setTextColor(textColor)
            holder.editName.setHintTextColor(textColor and 0x88FFFFFF.toInt())
            holder.editTemplate.setHintTextColor(textColor and 0x88FFFFFF.toInt())
            holder.dragHandle.setColorFilter(textColor)
        }
        override fun getItemCount() = fields.size
        inner class FieldViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val card: com.google.android.material.card.MaterialCardView = v as com.google.android.material.card.MaterialCardView
            val editName: TextInputEditText = v.findViewById(R.id.editFieldName)
            val editTemplate: TextInputEditText = v.findViewById(R.id.editFieldTemplate)
            val btnDelete: ImageButton = v.findViewById(R.id.btnDeleteField)
            val dragHandle: android.widget.ImageView = v.findViewById(R.id.dragHandle)
            var nameWatcher: android.text.TextWatcher? = null
            var templateWatcher: android.text.TextWatcher? = null
        }
    }
}
