package com.interli.plural.features.sysmedia

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.core.BaseActivity
import com.interli.plural.core.ColorHelper
import com.interli.plural.core.CropImageActivity
import com.interli.plural.core.ImageHelper
import com.interli.plural.features.diary.CreatePostActivity
import com.interli.plural.core.MediaEmbedHelper
import com.interli.plural.features.member.MemberHelper
import com.interli.plural.features.sysmedia.SysmediaPostDetailActivity
import com.interli.plural.Person
import com.interli.plural.R
import com.interli.plural.SysmediaPost
import com.interli.plural.SysmediaProfile
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

class SysmediaProfileActivity : BaseActivity() {
    private lateinit var posts: List<SysmediaPost>
    private lateinit var people: List<Person>
    private lateinit var activeMemberId: String
    private lateinit var profileUserId: String
    private lateinit var profileUser: Person
    private lateinit var markwon: io.noties.markwon.Markwon
    private var ivDialogProfilePreview: ImageView? = null
    private val gson = Gson()
    private val sdf = SimpleDateFormat("dd MMM yy", Locale.getDefault())
    private var currentProfileTab = 0
    private val pickImage = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        uri?.let {
            val internalUri = saveSourceImage(it)
            if (internalUri != null) {
                if (profileUser.sysmediaProfile == null) profileUser.sysmediaProfile = SysmediaProfile()
                profileUser.sysmediaProfile?.sourcePictureUri = internalUri.toString()
                startCropActivity(internalUri)
            }
        }
    }
    private val cropImageLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val croppedUriStr = result.data?.getStringExtra("cropped_uri")
            if (croppedUriStr != null) {
                applyCroppedImage(Uri.parse(croppedUriStr))
            }
        }
    }
    private fun startCropActivity(uri: Uri) {
        val intent = Intent(this, CropImageActivity::class.java)
        intent.putExtra("image_uri", uri.toString())
        cropImageLauncher.launch(intent)
    }
    private fun saveSourceImage(uri: Uri): Uri? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val file = java.io.File(filesDir, "sysmedia_profile_${profileUser.id}_source.jpg")
            val outputStream = java.io.FileOutputStream(file)
            inputStream.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            Uri.fromFile(file)
        } catch (e: Exception) { e.printStackTrace(); null }
    }
    private fun applyCroppedImage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val file = java.io.File(filesDir, "sysmedia_profile_${profileUser.id}_crop.jpg")
            val outputStream = java.io.FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            if (profileUser.sysmediaProfile == null) profileUser.sysmediaProfile = SysmediaProfile()
            val newUri = Uri.fromFile(file).toString()
            profileUser.sysmediaProfile?.profilePictureUri = newUri
            saveData()
            renderProfileInfo()
            ivDialogProfilePreview?.load(newUri)
        } catch (e: Exception) { e.printStackTrace() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sysmedia_profile)
        markwon = io.noties.markwon.Markwon.builder(this)
            .usePlugin(io.noties.markwon.linkify.LinkifyPlugin.create())
            .usePlugin(io.noties.markwon.ext.strikethrough.StrikethroughPlugin.create())
            .usePlugin(io.noties.markwon.image.coil.CoilImagesPlugin.create(this))
            .build()
        profileUserId = intent.getStringExtra("profile_user_id") ?: ""
        activeMemberId = intent.getStringExtra("active_member_id") ?: ""
        loadData()
        val foundUser = people.find { it.id == profileUserId }
        if (foundUser == null) {
            finish()
            return
        }
        profileUser = foundUser
        setupToolbar()
        renderProfileInfo()
        setupRecyclerView()
        val btnEdit = findViewById<MaterialButton>(R.id.btnEditProfile)
        val btnFollow = findViewById<MaterialButton>(R.id.btnFollow)
        if (profileUserId == activeMemberId) {
            btnEdit.visibility = View.VISIBLE
            btnFollow.visibility = View.GONE
        } else {
            btnEdit.visibility = View.GONE
            btnFollow.visibility = View.VISIBLE
            updateFollowButton()
        }
        btnEdit.setOnClickListener {
            showEditProfileDialog()
        }
        findViewById<View>(R.id.layoutFollowing).setOnClickListener {
            showUserListDialog("Following", profileUser.sysmediaProfile?.followingIds ?: emptyList())
        }

        findViewById<View>(R.id.layoutFollowers).setOnClickListener {
            val followerIds = people.filter { it.sysmediaProfile?.followingIds?.contains(profileUserId) == true }.map { it.id }
            showUserListDialog("Followers", followerIds)
        }

        btnFollow.setOnClickListener {
            val activePerson = people.find { it.id == activeMemberId }
            activePerson?.sysmediaProfile?.let { profile ->
                if (profile.followingIds.contains(profileUserId)) {
                    profile.followingIds.remove(profileUserId)
                } else {
                    profile.followingIds.add(profileUserId)
                }
                saveData()
                updateFollowButton()
            }
        }
        val bgColor = ColorHelper.getBgColor(this)
        val textColor = ColorHelper.getTextColor(this)
        findViewById<View>(R.id.main_content).setBackgroundColor(bgColor)
        findViewById<View>(R.id.appBarLayout).setBackgroundColor(bgColor)
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBar)
        toolbar.setBackgroundColor(bgColor)
        toolbar.setNavigationIconTint(textColor)
        toolbar.setTitleTextColor(textColor)
        ColorHelper.applySettings(this)
        btnEdit.setTextColor(ColorHelper.getBtnTextColor(this))
        btnEdit.strokeColor = android.content.res.ColorStateList.valueOf(ColorHelper.getBtnColor(this))

        val tabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.profileTabLayout)
        tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                currentProfileTab = tab?.position ?: 0
                updateRecyclerView()
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                updateRecyclerView()
            }
        })

        updateRecyclerView()
    }

    private fun updateRecyclerView() {
        val rv = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvProfilePosts)
        val displayPosts = if (currentProfileTab == 0) {
            posts.filter { it.senderId == profileUserId }
        } else {
            posts.filter { (it.likedByMemberIds[profileUserId] ?: 0) > 0 }
        }

        val sorted = displayPosts.sortedWith(compareByDescending<com.interli.plural.SysmediaPost> {
            it.id == profileUser.sysmediaProfile?.pinnedPostId
        }.thenByDescending { it.timestamp })

        (rv.adapter as? ProfilePostAdapter)?.let {
            it.items = sorted
            it.notifyDataSetChanged()
        } ?: run {
            rv.adapter = ProfilePostAdapter(sorted)
        }
    }
    private fun updateFollowButton() {
        val btnFollow = findViewById<MaterialButton>(R.id.btnFollow)
        val activePerson = people.find { it.id == activeMemberId }
        val isFollowing = activePerson?.sysmediaProfile?.followingIds?.contains(profileUserId) == true
        btnFollow.text = if (isFollowing) getString(R.string.action_unfollow) else getString(R.string.action_follow)
        val baseBtnColor = ColorHelper.getBtnColor(this)
        val btnTextColor = ColorHelper.getBtnTextColor(this)
        if (isFollowing) {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(baseBtnColor, hsv)
            hsv[2] = 1.0f
            hsv[1] = hsv[1] * 0.7f
            btnFollow.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.HSVToColor(hsv))
            btnFollow.setTextColor(android.graphics.Color.WHITE)
        } else {
            btnFollow.backgroundTintList = android.content.res.ColorStateList.valueOf(baseBtnColor)
            btnFollow.setTextColor(btnTextColor)
        }
    }
    private fun loadData() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val postsJson = sharedPref.getString("sysmedia_posts", "[]")
        posts = gson.fromJson(postsJson, object : TypeToken<List<SysmediaPost>>() {}.type) ?: emptyList()
        people = MemberHelper.loadAllPeople(this)
    }
    private fun saveData() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        sharedPref.edit(commit = true) {
            putString("sysmedia_posts", gson.toJson(posts))
        }
        MemberHelper.savePeople(this, people)
    }
    private fun showPhotoOptionsDialog() {
        val hasImage = !profileUser.sysmediaProfile?.profilePictureUri.isNullOrBlank()
        val options = mutableListOf(
            getString(R.string.upload_new_photo),
            getString(R.string.photo_via_link)
        )
        if (hasImage) options.add(getString(R.string.edit_current_picture))
        options.add(getString(R.string.delete_photo))
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.adjust_profile_photo))
            .setItems(options.toTypedArray()) { _, which ->
                val selected = options[which]
                when (selected) {
                    getString(R.string.upload_new_photo) -> pickImage.launch("image/*")
                    getString(R.string.photo_via_link) -> showUrlInputDialog()
                    getString(R.string.edit_current_picture) -> {
                        val sourceUri = profileUser.sysmediaProfile?.sourcePictureUri ?: profileUser.sysmediaProfile?.profilePictureUri
                        sourceUri?.let { uriStr ->
                            startCropActivity(Uri.parse(uriStr))
                        }
                    }
                    getString(R.string.delete_photo) -> {
                        profileUser.sysmediaProfile?.profilePictureUri = null
                        profileUser.sysmediaProfile?.sourcePictureUri = null
                        saveData()
                        renderProfileInfo()
                        ivDialogProfilePreview?.setImageResource(R.mipmap.ic_launcher)
                    }
                }
            }
            .show()
    }
    private fun showUrlInputDialog() {
        val input = EditText(this).apply {
            hint = "https://example.com/image.jpg"
            setTextColor(ColorHelper.getTextColor(this@SysmediaProfileActivity))
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = 24.dpToPx()
            setPadding(padding, 8.dpToPx(), padding, 8.dpToPx())
            addView(input)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.photo_via_link))
            .setView(container)
            .setPositiveButton(getString(R.string.load)) { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    lifecycleScope.launch {
                        if (profileUser.sysmediaProfile == null) profileUser.sysmediaProfile = SysmediaProfile()
                        val localUri = ImageHelper.downloadAndSaveProfilePicture(this@SysmediaProfileActivity, url, profileUser.id)
                        if (localUri != null) {
                            profileUser.sysmediaProfile?.sourcePictureUri = localUri
                            startCropActivity(Uri.parse(localUri))
                        } else {
                            profileUser.sysmediaProfile?.profilePictureUri = url
                            profileUser.sysmediaProfile?.sourcePictureUri = url
                            saveData()
                            renderProfileInfo()
                            ivDialogProfilePreview?.load(profileUser.sysmediaProfile?.profilePictureUri)
                        }
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        dialog.show()
        ColorHelper.styleAlertDialog(dialog, this)
    }
    private fun showEditProfileDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.action_edit_sysmedia_profile))
        val view = layoutInflater.inflate(R.layout.dialog_edit_sysmedia_profile, null)
        val etName = view.findViewById<EditText>(R.id.etName)
        val etHandle = view.findViewById<EditText>(R.id.etHandle)
        val etBio = view.findViewById<EditText>(R.id.etBio)
        val container = view as ViewGroup
        val profileContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(84.dpToPx(), 84.dpToPx()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 16.dpToPx()
            }
        }
        val cardView = MaterialCardView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            radius = 42.dpToPx().toFloat()
            strokeWidth = 2.dpToPx()
            strokeColor = ColorHelper.getBtnColor(this@SysmediaProfileActivity)
            cardElevation = 0f
        }
        val ivProfile = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        cardView.addView(ivProfile)
        profileContainer.addView(cardView)
        val ivEditIcon = ImageView(this).apply {
            val size = 28.dpToPx()
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.BOTTOM or Gravity.END
            }
            setImageResource(android.R.drawable.ic_menu_camera)
            val padding = 6.dpToPx()
            setPadding(padding, padding, padding, padding)
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ColorHelper.getBtnColor(this@SysmediaProfileActivity))
            }
            background = shape
            setColorFilter(ColorHelper.getBtnTextColor(this@SysmediaProfileActivity))
        }
        profileContainer.addView(ivEditIcon)
        container.addView(profileContainer, 0)
        ivDialogProfilePreview = ivProfile
        val profile = profileUser.sysmediaProfile ?: SysmediaProfile()
        val currentAvatar = profile.profilePictureUri ?: profileUser.profilePictureUri
        if (currentAvatar != null) ivProfile.load(currentAvatar) else ivProfile.setImageResource(R.mipmap.ic_launcher)
        profileContainer.setOnClickListener {
            showPhotoOptionsDialog()
        }
        etName.setText(profile.displayName ?: profileUser.name)
        etHandle.setText(profile.handle ?: profileUser.manualId ?: profileUser.name.replace(" ", "_").lowercase())
        etBio.setText(profile.bio ?: "")
        builder.setView(view)
        builder.setPositiveButton(getString(R.string.save)) { _, _ ->
            val newName = etName.text.toString().trim()
            val newHandle = etHandle.text.toString().trim()
            val newBio = etBio.text.toString().trim()
            profileUser.sysmediaProfile?.let {
                it.displayName = newName
                it.handle = newHandle
                it.bio = newBio
            } ?: run {
                profileUser.sysmediaProfile = SysmediaProfile(displayName = newName, handle = newHandle, bio = newBio)
            }
            saveData()
            renderProfileInfo()
            ivDialogProfilePreview = null
        }
        builder.setNegativeButton(getString(R.string.cancel)) { _, _ ->
            ivDialogProfilePreview = null
        }
        builder.setOnDismissListener { ivDialogProfilePreview = null }
        val dialog = builder.create()
        dialog.show()
        ColorHelper.styleAlertDialog(dialog, this)
        etName.setTextColor(ColorHelper.getTextColor(this))
        etHandle.setTextColor(ColorHelper.getTextColor(this))
        etBio.setTextColor(ColorHelper.getTextColor(this))
    }
    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBar)
        toolbar.setNavigationOnClickListener { finish() }
    }
    private fun renderProfileInfo() {
        val textColor = ColorHelper.getTextColor(this)
        val profile = profileUser.sysmediaProfile

        findViewById<TextView>(R.id.tvProfileName).apply {
            text = profile?.displayName ?: profileUser.name
            setTextColor(textColor)
        }

        val handle = profile?.handle ?: profileUser.name.replace(" ", "_").lowercase().replace(Regex("[^a-z0-9_]"), "")
        findViewById<TextView>(R.id.tvProfileHandle).apply {
            text = "@$handle"
            setTextColor(textColor and 0x88FFFFFF.toInt())
        }

        findViewById<TextView>(R.id.tvProfileBio).apply {
            markwon.setMarkdown(this, (profile?.bio ?: "No bio").replace("\n", "  \n"))
            setTextColor(textColor)
        }

        val mediaContainer = findViewById<LinearLayout>(R.id.mediaEmbedContainerProfile)
        MediaEmbedHelper.addEmbedsToContainer(mediaContainer, profile?.bio ?: "")

        val avatarUri = profile?.profilePictureUri ?: profileUser.profilePictureUri
        val ivAvatar = findViewById<ImageView>(R.id.ivProfileAvatar)
        if (avatarUri != null) {
            ivAvatar.load(avatarUri)
        } else {
            val color = if (profileUser.profileColor == -6934396) ColorHelper.getBtnColor(this) else profileUser.profileColor
            ivAvatar.setImageDrawable(android.graphics.drawable.ColorDrawable(color))
        }

        val followingCount = profile?.followingIds?.size ?: 0
        findViewById<TextView>(R.id.tvFollowingCount).text = followingCount.toString()

        val followersCount = people.count { it.sysmediaProfile?.followingIds?.contains(profileUserId) == true }
        findViewById<TextView>(R.id.tvFollowersCount).text = followersCount.toString()

        findViewById<TextView>(R.id.tvFollowingCount).setTextColor(textColor)
        findViewById<TextView>(R.id.tvFollowersCount).setTextColor(textColor)
        findViewById<TextView>(R.id.tvFollowingLabel).setTextColor(textColor and 0x88FFFFFF.toInt())
        findViewById<TextView>(R.id.tvFollowersLabel).setTextColor(textColor and 0x88FFFFFF.toInt())
    }
    private fun setupRecyclerView() {
        val rv = findViewById<RecyclerView>(R.id.rvProfilePosts)
        rv.layoutManager = LinearLayoutManager(this)
        val userPosts = posts.filter { it.senderId == profileUserId }.sortedByDescending { it.timestamp }
        rv.adapter = ProfilePostAdapter(userPosts)
    }

    private fun downloadPostAsImage(view: View, post: SysmediaPost) {
        val btns = listOf(R.id.btnReply, R.id.btnReblog, R.id.btnLike, R.id.btnShare)
        btns.forEach { view.findViewById<View>(it)?.visibility = View.INVISIBLE }

        val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(ColorHelper.getBgColor(this))
        view.draw(canvas)

        btns.forEach { view.findViewById<View>(it)?.visibility = View.VISIBLE }

        val fileName = "sysmedia_${post.id.take(5)}_${System.currentTimeMillis()}.jpg"
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES)
            }
        }

        val uri = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            contentResolver.openOutputStream(it)?.use { os ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, os)
            }
            Toast.makeText(this, getString(R.string.saved_in, "Galerij"), Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderPoll(holder: ProfilePostAdapter.ViewHolder, post: SysmediaPost) {
        val poll = post.poll
        if (poll == null) {
            holder.layoutPoll.visibility = View.GONE
            return
        }
        holder.layoutPoll.visibility = View.VISIBLE
        holder.layoutPoll.removeAllViews()
        val textColor = ColorHelper.getTextColor(this)
        val btnColor = ColorHelper.getBtnColor(this)
        val activeUserId = activeMemberId
        val hasVoted = poll.votes.containsKey(activeUserId)
        val totalVotes = poll.votes.size
        poll.options.forEachIndexed { index, option ->
            val optionLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8.dpToPx() }
            }
            if (hasVoted) {
                val votesForThis = poll.votes.values.count { it == index }
                val percentage = if (totalVotes > 0) (votesForThis * 100 / totalVotes) else 0
                val isMyVote = poll.votes[activeUserId] == index
                val resultRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                resultRow.addView(TextView(this).apply {
                    text = option
                    setTextColor(textColor)
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                    if (isMyVote) setTypeface(null, android.graphics.Typeface.BOLD)
                })
                if (isMyVote) {
                    resultRow.addView(ImageView(this).apply {
                        setImageResource(android.R.drawable.checkbox_on_background)
                        setColorFilter(btnColor)
                        layoutParams = LinearLayout.LayoutParams(16.dpToPx(), 16.dpToPx()).apply { marginStart = 4.dpToPx() }
                    })
                }
                resultRow.addView(TextView(this).apply {
                    text = "$percentage%"
                    setTextColor(textColor)
                    setPadding(8.dpToPx(), 0, 0, 0)
                })
                optionLayout.addView(resultRow)
                val progressBarContainer = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(-1, 4.dpToPx()).apply { topMargin = 4.dpToPx() }
                    setBackgroundColor(textColor and 0x22FFFFFF)
                    weightSum = 100f
                }
                val progressFill = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, -1, percentage.toFloat())
                    setBackgroundColor(btnColor)
                }
                progressBarContainer.addView(progressFill)
                optionLayout.addView(progressBarContainer)
            } else {
                optionLayout.addView(com.google.android.material.button.MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = option
                    setTextColor(btnColor)
                    strokeColor = android.content.res.ColorStateList.valueOf(btnColor)
                    isAllCaps = false
                    setOnClickListener {
                        poll.votes[activeUserId] = index
                        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
                        sharedPref.edit {
                            putString("sysmedia_posts", gson.toJson(posts))
                        }
                        setupRecyclerView()
                    }
                })
            }
            holder.layoutPoll.addView(optionLayout)
        }
        if (totalVotes > 0) {
            holder.layoutPoll.addView(TextView(this).apply {
                text = getString(R.string.poll_votes_count, totalVotes)
                textSize = 12f
                alpha = 0.6f
                setTextColor(textColor)
            })
        }
    }
    private inner class ProfilePostAdapter(var items: List<SysmediaPost>) :
        RecyclerView.Adapter<ProfilePostAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivAvatar: ImageView = view.findViewById(R.id.ivAvatar)
            val tvName: TextView = view.findViewById(R.id.tvName)
            val tvHandle: TextView = view.findViewById(R.id.tvHandle)
            val tvContent: TextView = view.findViewById(R.id.tvContent)
            val tvTime: TextView = view.findViewById(R.id.tvTimeBottom)
            val ivContentImage: ImageView? = view.findViewById(R.id.ivPostImagePreview)
            val mediaEmbedContainer: LinearLayout = view.findViewById(R.id.mediaEmbedContainer)
            val layoutReblog: View = view.findViewById(R.id.layoutReblogHeader)
            val tvRebloggedBy: TextView = view.findViewById(R.id.tvRebloggedBy)
            val cardOriginal: MaterialCardView = view.findViewById(R.id.cardOriginalPost)
            val ivOriginalAvatar: ImageView = view.findViewById(R.id.ivOriginalAvatar)
            val tvOriginalName: TextView = view.findViewById(R.id.tvOriginalName)
            val tvOriginalHandle: TextView = view.findViewById(R.id.tvOriginalHandle)
            val tvOriginalContent: TextView = view.findViewById(R.id.tvOriginalContent)
            val ivOriginalPostImage: ImageView = view.findViewById(R.id.ivOriginalPostImage)
            val layoutThreadParent: View = view.findViewById(R.id.layoutThreadParent)
            val layoutPoll: LinearLayout = view.findViewById(R.id.layoutPoll)
            val tvLikes: TextView = view.findViewById(R.id.tvLikes)
            val tvRetweets: TextView = view.findViewById(R.id.tvRetweets)
            val tvReplies: TextView = view.findViewById(R.id.tvReplies)
            val btnLike: View = view.findViewById(R.id.btnLike)
            val btnReblog: View = view.findViewById(R.id.btnReblog)
            val btnReply: View = view.findViewById(R.id.btnReply)
            val btnShare: View = view.findViewById(R.id.btnShare)
            val ivParentAvatar: ImageView = view.findViewById(R.id.ivParentAvatar)
            val tvParentName: TextView = view.findViewById(R.id.tvParentName)
            val tvParentContent: TextView = view.findViewById(R.id.tvParentContent)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sysmedia_post, parent, false)
            return ViewHolder(view)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val post = items[position]
            val sender = people.find { it.id == post.senderId }
            val profile = sender?.sysmediaProfile
            val textColor = ColorHelper.getTextColor(this@SysmediaProfileActivity)

            holder.layoutReblog.visibility = View.GONE
            holder.cardOriginal.visibility = View.GONE
            holder.layoutThreadParent.visibility = View.GONE

            if (post.replyToId != null) {
                val parentPost = posts.find { it.id == post.replyToId }
                if (parentPost != null) {
                    holder.layoutThreadParent.visibility = View.VISIBLE
                    val parentSender = people.find { it.id == parentPost.senderId }
                    val parentProfile = parentSender?.sysmediaProfile
                    holder.tvParentName.text = parentProfile?.displayName ?: parentSender?.name ?: "Unknown"
                    holder.tvParentContent.text = parentPost.content
                    val parentAvatar = parentProfile?.profilePictureUri ?: parentSender?.profilePictureUri
                    val pColor = parentSender?.profileColor ?: ColorHelper.getBtnColor(this@SysmediaProfileActivity)
                    if (parentAvatar != null) holder.ivParentAvatar.load(parentAvatar) { error(android.graphics.drawable.ColorDrawable(pColor)) }
                    else holder.ivParentAvatar.setImageDrawable(android.graphics.drawable.ColorDrawable(pColor))
                    holder.tvParentName.setTextColor(textColor and 0xCCFFFFFF.toInt())
                    holder.tvParentContent.setTextColor(textColor and 0xCCFFFFFF.toInt())
                }
            }

            if (post.reblogOfId != null) {
                holder.layoutReblog.visibility = View.VISIBLE
                holder.tvRebloggedBy.text = getString(R.string.reblogged_by, profileUser.name)
                holder.tvRebloggedBy.setTextColor(textColor and 0x88FFFFFF.toInt())
                val originalPost = posts.find { it.id == post.reblogOfId }
                if (originalPost != null) {
                    holder.cardOriginal.visibility = View.VISIBLE
                    holder.cardOriginal.setCardBackgroundColor(ColorHelper.getBgColor(this@SysmediaProfileActivity))
                    val originalSender = people.find { it.id == originalPost.senderId }
                    val origProfile = originalSender?.sysmediaProfile
                    holder.tvOriginalName.text = origProfile?.displayName ?: originalSender?.name ?: "Unknown"
                    val origHandle = origProfile?.handle ?: originalSender?.name?.replace(" ", "_")?.lowercase()?.replace(Regex("[^a-z0-9_]"), "") ?: "unknown"
                    holder.tvOriginalHandle.text = "@$origHandle"
                    markwon.setMarkdown(holder.tvOriginalContent, originalPost.content.replace("\n", "  \n"))
                    val origAvatar = origProfile?.profilePictureUri ?: originalSender?.profilePictureUri
                    if (origAvatar != null) holder.ivOriginalAvatar.load(origAvatar) { placeholder(android.graphics.drawable.ColorDrawable(originalSender?.profileColor ?: ColorHelper.getBtnColor(this@SysmediaProfileActivity))) }
                    else holder.ivOriginalAvatar.setImageDrawable(android.graphics.drawable.ColorDrawable(originalSender?.profileColor ?: ColorHelper.getBtnColor(this@SysmediaProfileActivity)))
                    if (originalPost.imageUri != null) { holder.ivOriginalPostImage.visibility = View.VISIBLE; holder.ivOriginalPostImage.load(originalPost.imageUri) }
                    else holder.ivOriginalPostImage.visibility = View.GONE
                    holder.tvOriginalName.setTextColor(textColor); holder.tvOriginalContent.setTextColor(textColor); holder.tvOriginalHandle.setTextColor(textColor and 0x88FFFFFF.toInt())
                }
            }

            holder.tvName.apply {
                text = profile?.displayName ?: sender?.name ?: "Unknown"
                setTextColor(textColor)
            }
            val handle = profile?.handle ?: sender?.name?.replace(" ", "_")?.lowercase()?.replace(Regex("[^a-z0-9_]"), "") ?: "unknown"
            holder.tvHandle.apply {
                text = "@$handle"
                setTextColor(textColor and 0x88FFFFFF.toInt())
            }

            holder.tvContent.apply {
                markwon.setMarkdown(this, post.content.replace("\n", "  \n"))
                setTextColor(textColor)
                setOnClickListener { holder.itemView.performClick() }
            }

            holder.tvTime.apply {
                text = sdf.format(Date(post.timestamp))
                setTextColor(textColor and 0x88FFFFFF.toInt())
            }

            val avatarUri = profile?.profilePictureUri ?: sender?.profilePictureUri
            val userColor = if ((sender?.profileColor ?: -6934396) == -6934396) ColorHelper.getBtnColor(this@SysmediaProfileActivity) else sender?.profileColor ?: -6934396
            if (avatarUri != null) {
                holder.ivAvatar.load(avatarUri) {
                    placeholder(android.graphics.drawable.ColorDrawable(userColor))
                    error(android.graphics.drawable.ColorDrawable(userColor))
                }
            } else {
                holder.ivAvatar.setImageDrawable(android.graphics.drawable.ColorDrawable(userColor))
            }

            holder.tvLikes.text = post.likes.toString()
            holder.tvRetweets.text = post.retweets.toString()
            holder.tvReplies.text = post.replies.toString()
            val activeLikes = post.likedByMemberIds[activeMemberId] ?: 0
            val likeColor = if (activeLikes > 0) ColorHelper.getBtnColor(this@SysmediaProfileActivity) else textColor and 0x88FFFFFF.toInt()
            holder.tvLikes.setTextColor(likeColor)
            holder.itemView.findViewById<ImageView>(R.id.ivLikeIcon)?.setColorFilter(likeColor)
            holder.btnLike.setOnClickListener {
                val currentLikes = post.likedByMemberIds[activeMemberId] ?: 0
                if (currentLikes < 3) { post.likedByMemberIds[activeMemberId] = currentLikes + 1; post.likes++ }
                else { post.likedByMemberIds[activeMemberId] = 0; post.likes -= 3 }
                saveData(); notifyItemChanged(position)
            }
            if (post.imageUri != null) { holder.ivContentImage?.visibility = View.VISIBLE; holder.ivContentImage?.load(post.imageUri) }
            else holder.ivContentImage?.visibility = View.GONE

            MediaEmbedHelper.addEmbedsToContainer(holder.mediaEmbedContainer, post.content)
            renderPoll(holder, post)

            holder.itemView.setOnLongClickListener {
                if (post.senderId == activeMemberId) {
                    val isPinned = profileUser.sysmediaProfile?.pinnedPostId == post.id
                    val options = mutableListOf(getString(R.string.action_edit_post), getString(R.string.delete))

                    if (profileUserId == activeMemberId) {
                        options.add(if (isPinned) getString(R.string.unpin_from_profile) else getString(R.string.pin_to_profile))
                    }

                    AlertDialog.Builder(this@SysmediaProfileActivity)
                        .setItems(options.toTypedArray()) { _, which ->
                            val selected = options[which]
                            when (selected) {
                                getString(R.string.action_edit_post) -> {
                                    val intent = Intent(this@SysmediaProfileActivity, CreatePostActivity::class.java)
                                    intent.putExtra("current_user_id", activeMemberId)
                                    intent.putExtra("edit_post_id", post.id)
                                    startActivity(intent)
                                }
                                getString(R.string.delete) -> {
                                    val mutablePosts = posts.toMutableList()
                                    mutablePosts.remove(post)
                                    posts = mutablePosts
                                    saveData()
                                    updateRecyclerView()
                                }
                                getString(R.string.pin_to_profile), getString(R.string.unpin_from_profile) -> {
                                    if (profileUser.sysmediaProfile == null) profileUser.sysmediaProfile = SysmediaProfile()
                                    profileUser.sysmediaProfile?.pinnedPostId = if (isPinned) null else post.id
                                    saveData()
                                    updateRecyclerView()
                                    Toast.makeText(this@SysmediaProfileActivity, if (isPinned) getString(R.string.post_unpinned) else getString(R.string.post_pinned), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }.show()
                }
                true
            }

            holder.btnReblog.setOnClickListener { val intent = Intent(this@SysmediaProfileActivity, CreatePostActivity::class.java); intent.putExtra("current_user_id", activeMemberId); intent.putExtra("reblog_of_id", post.id); startActivity(intent) }
            holder.btnReply.setOnClickListener { val intent = Intent(this@SysmediaProfileActivity, CreatePostActivity::class.java); intent.putExtra("current_user_id", activeMemberId); intent.putExtra("reply_to_id", post.id); startActivity(intent) }
            holder.btnShare.setOnClickListener { downloadPostAsImage(holder.itemView, post) }
            holder.itemView.setOnClickListener {
                val intent = Intent(this@SysmediaProfileActivity, SysmediaPostDetailActivity::class.java)
                intent.putExtra("post_id", post.id)
                intent.putExtra("active_member_id", activeMemberId)
                startActivity(intent)
            }
        }
        override fun getItemCount() = items.size
    }
    private fun showUserListDialog(title: String, userIds: List<String>) {
        val filteredPeople = people.filter { userIds.contains(it.id) }

        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(p: ViewGroup, t: Int) = object : RecyclerView.ViewHolder(
                LayoutInflater.from(p.context).inflate(R.layout.item_sysmedia_account, p, false)) {}

            override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
                val person = filteredPeople[pos]
                val prof = person.sysmediaProfile
                h.itemView.findViewById<TextView>(R.id.tvName).text = prof?.displayName ?: person.name
                h.itemView.findViewById<TextView>(R.id.tvHandle).text = "@${prof?.handle ?: person.name}"
                val iv = h.itemView.findViewById<ImageView>(R.id.ivAvatar)
                val uri = prof?.profilePictureUri ?: person.profilePictureUri
                if (uri != null) iv.load(uri) else iv.setImageResource(R.drawable.ic_stat_name)

                h.itemView.setOnClickListener {
                    val intent = Intent(this@SysmediaProfileActivity, SysmediaProfileActivity::class.java)
                    intent.putExtra("profile_user_id", person.id)
                    intent.putExtra("active_member_id", activeMemberId)
                    startActivity(intent)
                }
            }
            override fun getItemCount() = filteredPeople.size
        }

        val rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@SysmediaProfileActivity)
            this.adapter = adapter
        }

        AlertDialog.Builder(this).setTitle(title).setView(rv).setPositiveButton(R.string.done, null).show()
    }
}