package com.interli.plural.core

import android.content.Context
import android.graphics.Color
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.interli.plural.ChatGroup
import com.interli.plural.CustomField
import com.interli.plural.DiaryNote
import com.interli.plural.DirectMessage
import com.interli.plural.FrontSession
import com.interli.plural.Group
import com.interli.plural.Person
import com.interli.plural.SysmediaPoll
import com.interli.plural.SysmediaPost
import com.interli.plural.SysmediaProfile
import com.interli.plural.features.diary.ChatActivity
import com.interli.plural.features.member.MemberHelper
import com.interli.plural.features.mood.MoodActivity
import com.interli.plural.features.relations.NodeType
import com.interli.plural.features.relations.RelationEdge
import com.interli.plural.features.relations.RelationEnvironment
import com.interli.plural.features.relations.RelationNode
import com.interli.plural.features.relations.RelationsData
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.zip.ZipInputStream

class PluralSpaceImporter(private val context: Context) {

    data class ImportResult(
        val members: Int = 0,
        val fronts: Int = 0,
        val moods: Int = 0,
        val notes: Int = 0,
        val chatChannels: Int = 0,
        val chatMessages: Int = 0,
        val polls: Int = 0,
        val groups: Int = 0,
        val customFields: Int = 0,
        val relationships: Int = 0
    ) {
        val total: Int
            get() = members + fronts + moods + notes + chatChannels +
                    chatMessages + polls + groups + customFields + relationships
    }

    private val gson = Gson()

    fun import(uri: Uri): ImportResult {
        val jsonText = readDataJson(uri)
        val root = JsonParser.parseString(jsonText).asJsonObject

        require(root.has("members") && root.has("fronts")) {
            "This ZIP does not contain the expected PluralSpace data."
        }

        val people = MemberHelper.loadAllPeople(context).toMutableList()
        val sourceMemberToPerson = mutableMapOf<String, Person>()
        var membersAddedOrUpdated = 0

        root.array("members").forEach { source ->
            val sourceId = source.string("id")
            if (sourceId.isBlank()) return@forEach

            val name = source.string("display_name")
                .ifBlank { source.string("name") }
                .ifBlank { "Unknown" }

            val existing = people.firstOrNull { it.manualId == sourceId }
            val person = existing ?: Person(manualId = sourceId)

            person.name = name
            person.isArchived = source.boolean("is_archived")
            person.profileColor = source.string("color").toColor(person.profileColor)

            val profileLines = mutableListOf<String>()
            source.string("pronouns").takeIf { it.isNotBlank() }?.let {
                profileLines.add("Pronouns: $it")
            }
            source.stringArray("role").joinToString(", ")
                .takeIf { it.isNotBlank() }
                ?.let { profileLines.add("Role: $it") }

            source.string("description").takeIf { it.isNotBlank() }?.let {
                profileLines.add(it)
            }
            person.profileInfo = profileLines.joinToString("\n")

            if (person.sysmediaProfile == null) {
                person.sysmediaProfile = SysmediaProfile()
            }
            person.sysmediaProfile?.handle = name
                .replace(" ", "_")
                .lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9_]"), "")

            if (existing == null) {
                people.add(person)
                membersAddedOrUpdated++
            } else {
                membersAddedOrUpdated++
            }

            sourceMemberToPerson[sourceId] = person
        }

        importAvatars(uri, root, sourceMemberToPerson)
        MemberHelper.savePeople(context, people)

        val groupsImported = importGroups(root, people)
        val customFieldsImported =
            importCustomFields(root, people, sourceMemberToPerson)
        MemberHelper.savePeople(context, people)

        val frontsImported = importFronts(root, people, sourceMemberToPerson)
        val notesImported = importNotes(root, people, sourceMemberToPerson)
        val moodsImported = importMoods(root, people, sourceMemberToPerson)
        val chats = importChats(root, people, sourceMemberToPerson)
        val pollsImported = importPolls(root, people, sourceMemberToPerson)
        val relationshipsImported = importRelationships(root, people, sourceMemberToPerson)

        MemberHelper.savePeople(context, people)

        return ImportResult(
            members = membersAddedOrUpdated,
            fronts = frontsImported,
            moods = moodsImported,
            notes = notesImported,
            chatChannels = chats.first,
            chatMessages = chats.second,
            polls = pollsImported,
            groups = groupsImported,
            customFields = customFieldsImported,
            relationships = relationshipsImported
        )
    }

    private fun readDataJson(uri: Uri): String {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val fileName = entry.name
                        .substringAfterLast('/')
                        .substringAfterLast('\\')

                    if (!entry.isDirectory && fileName.equals("data.json", true)) {
                        return zip.readBytes().toString(Charsets.UTF_8)
                    }

                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        error("Could not find data.json inside the ZIP.")
    }

    private fun importAvatars(
        uri: Uri,
        root: JsonObject,
        peopleBySourceId: Map<String, Person>
    ) {
        val wantedPaths = root.array("members")
            .mapNotNull { member ->
                val id = member.string("id")
                val path = member.string("avatar_media_path")
                if (id.isNotBlank() && path.isNotBlank()) id to normalizePath(path) else null
            }
            .toMap()

        if (wantedPaths.isEmpty()) return

        val unresolved = wantedPaths.toMutableMap()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry

                while (entry != null && unresolved.isNotEmpty()) {
                    if (!entry.isDirectory) {
                        val entryPath = normalizePath(entry.name)
                        val matchedId = unresolved.entries.firstOrNull { (_, wanted) ->
                            entryPath == wanted ||
                                    entryPath.endsWith("/$wanted") ||
                                    entryPath.substringAfterLast('/') == wanted.substringAfterLast('/')
                        }?.key

                        if (matchedId != null) {
                            val bytes = zip.readBytes()
                            if (bytes.size <= 20 * 1024 * 1024) {
                                val person = peopleBySourceId[matchedId]
                                if (person != null) {
                                    val safeName = entryPath.substringAfterLast('/')
                                        .replace(Regex("[^A-Za-z0-9._-]"), "_")
                                    val file = File(
                                        context.filesDir,
                                        "pluralspace_${person.id}_$safeName"
                                    )
                                    file.writeBytes(bytes)
                                    person.profilePictureUri = Uri.fromFile(file).toString()
                                }
                            }
                            unresolved.remove(matchedId)
                        }
                    }

                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
    }

    private fun importGroups(root: JsonObject, people: MutableList<Person>): Int {
        val prefs = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
        val existing = readList<Group>(prefs.getString("groups_list", "[]"))
            .associateBy { it.id }
            .toMutableMap()

        root.array("member_groups").forEach { source ->
            val sourceId = source.string("id")
            if (sourceId.isBlank()) return@forEach

            val group = existing[sourceId] ?: Group(
                id = sourceId,
                name = source.string("name").ifBlank { "Imported group" }
            )

            group.name = source.string("name").ifBlank { group.name }
            group.parentGroupId = source.string("parent_id").takeIf { it.isNotBlank() }
            group.color = source.string("color").toColor(group.color)
            existing[sourceId] = group

            source.array("members").forEach { memberRef ->
                val memberId = memberRef.asSourceId()
                val person = people.firstOrNull { it.manualId == memberId }
                    ?: people.firstOrNull { it.name.equals(memberId, true) }
                if (person != null && !person.safeGroupIds.contains(group.id)) {
                    person.safeGroupIds.add(group.id)
                }
            }
        }

        val groupIds = existing.keys
        root.array("members").forEach { member ->
            val person = people.firstOrNull { it.manualId == member.string("id") } ?: return@forEach
            member.stringArray("groups").filter { it in groupIds }.forEach { groupId ->
                if (!person.safeGroupIds.contains(groupId)) {
                    person.safeGroupIds.add(groupId)
                }
            }
        }

        prefs.edit().putString("groups_list", gson.toJson(existing.values.toList())).apply()
        return root.array("member_groups").size()
    }

    private fun importCustomFields(
        root: JsonObject,
        people: MutableList<Person>,
        peopleBySourceId: Map<String, Person>
    ): Int {
        val prefs = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

        val existing = readList<CustomField>(
            prefs.getString("custom_fields", "[]")
        )
            .filter { !it.id.isNullOrBlank() }
            .associateBy { it.id!! }
            .toMutableMap()

        val definitions = mutableListOf<CustomField>()

        root.array("custom_fields").forEach fieldLoop@ { sourceField ->
            val sourceId = sourceField.string("id")
            val name = sourceField.string("name")

            if (sourceId.isBlank() || name.isBlank()) return@fieldLoop

            val field = existing[sourceId] ?: CustomField(
                id = sourceId,
                name = name,
                template = ""
            )

            field.name = name
            existing[sourceId] = field
            definitions.add(field)
        }

        val definitionsByName = definitions.groupBy {
            it.name.trim().lowercase(Locale.ROOT)
        }

        root.array("members").forEach memberLoop@ { sourceMember ->
            val sourceMemberId = sourceMember.string("id")
            val person = peopleBySourceId[sourceMemberId] ?: return@memberLoop

            val occurrenceByName = mutableMapOf<String, Int>()
            val valuesByFieldId = linkedMapOf<String, MutableList<String>>()

            sourceMember.array("custom_field_values").forEach valueLoop@ { sourceValue ->
                val fieldName = sourceValue.string("field_name").trim()
                val value = sourceValue.string("value")

                if (fieldName.isBlank() || value.isBlank()) return@valueLoop

                val key = fieldName.lowercase(Locale.ROOT)
                val matchingDefinitions = definitionsByName[key]
                    ?: return@valueLoop

                val occurrence = occurrenceByName[key] ?: 0
                occurrenceByName[key] = occurrence + 1

                val definitionIndex = if (matchingDefinitions.size == 1) {
                    0
                } else {
                    occurrence.coerceAtMost(matchingDefinitions.lastIndex)
                }

                val fieldId = matchingDefinitions[definitionIndex].id
                    ?: return@valueLoop

                valuesByFieldId
                    .getOrPut(fieldId) { mutableListOf() }
                    .add(value)
            }

            valuesByFieldId.forEach { (fieldId, values) ->
                person.safeCustomFields[fieldId] = values.joinToString("\n")
            }
        }

        prefs.edit()
            .putString("custom_fields", gson.toJson(existing.values.toList()))
            .apply()

        return root.array("custom_fields").size()
    }

    private fun importFronts(
        root: JsonObject,
        people: MutableList<Person>,
        peopleBySourceId: Map<String, Person>
    ): Int {
        val prefs = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
        val sessions = readList<FrontSession>(prefs.getString("sessions_list", "[]"))
            .toMutableList()
        var imported = 0

        root.array("fronts").forEach { source ->
            val sourceMemberId = source.string("member_id")
            val person = peopleBySourceId[sourceMemberId]
                ?: people.firstOrNull { it.name.equals(source.string("member_name"), true) }
                ?: return@forEach

            val start = source.timestamp("started_at")
            if (start <= 0L) return@forEach
            val end = source.timestamp("ended_at").takeIf { it > 0L }
            val note = source.string("comment").takeIf { it.isNotBlank() }
            val role = source.string("type_name").takeIf { it.isNotBlank() }

            val duplicate = sessions.any {
                it.personId == person.id &&
                        it.startTime == start &&
                        it.endTime == end &&
                        it.note == note
            }
            if (!duplicate) {
                sessions.add(
                    FrontSession(
                        personName = person.name,
                        startTime = start,
                        endTime = end,
                        personId = person.id,
                        note = note,
                        frontPercentage = null,
                        frontRole = role
                    )
                )
                imported++
            }
        }

        prefs.edit().putString("sessions_list", gson.toJson(sessions)).apply()
        return imported
    }

    private fun importNotes(
        root: JsonObject,
        people: MutableList<Person>,
        peopleBySourceId: Map<String, Person>
    ): Int {
        val prefs = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
        val notes = readList<DiaryNote>(prefs.getString("diary_notes", "[]"))
            .toMutableList()
        var imported = 0

        fun addNote(
            id: String,
            title: String,
            content: String,
            createdAt: Long,
            updatedAt: Long,
            linkedIds: List<String>
        ) {
            if (content.isBlank() || notes.any { it.id == id }) return

            notes.add(
                DiaryNote(
                    id = id,
                    title = title.ifBlank { content.take(60) },
                    content = content,
                    timestamp = createdAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    noteEditTimestamp = updatedAt.takeIf { it > 0L },
                    linkedMemberIds = linkedIds.toMutableList()
                )
            )
            imported++
        }

        root.array("notes").forEach { source ->
            val sourceId = source.string("id")
            if (sourceId.isBlank()) return@forEach
            val member = source.string("member")
            val person = people.firstOrNull { it.name.equals(member, true) }
            addNote(
                id = "pluralspace-note-$sourceId",
                title = source.string("title"),
                content = source.string("content"),
                createdAt = source.timestamp("created_at"),
                updatedAt = source.timestamp("updated_at"),
                linkedIds = listOfNotNull(person?.id)
            )
        }

        root.array("journal_entries").forEach { source ->
            val sourceId = source.string("id")
            if (sourceId.isBlank()) return@forEach
            val linkedIds = source.array("members").mapNotNull { memberRef ->
                resolvePerson(
                    memberRef,
                    people,
                    peopleBySourceId,
                    createUnknown = false
                )?.id
            }
            addNote(
                id = "pluralspace-journal-$sourceId",
                title = source.string("title"),
                content = source.string("content"),
                createdAt = source.timestamp("created_at"),
                updatedAt = source.timestamp("updated_at"),
                linkedIds = linkedIds
            )
        }

        root.array("thoughts").forEach { source ->
            val sourceId = source.string("id")
            if (sourceId.isBlank()) return@forEach
            val memberName = source.string("member")
            val person = people.firstOrNull { it.name.equals(memberName, true) }
            val content = source.string("content")
            addNote(
                id = "pluralspace-thought-$sourceId",
                title = memberName.ifBlank { content.take(60) },
                content = content,
                createdAt = source.timestamp("created_at"),
                updatedAt = 0L,
                linkedIds = listOfNotNull(person?.id)
            )
        }

        prefs.edit().putString("diary_notes", gson.toJson(notes)).apply()
        return imported
    }

    private fun importMoods(
        root: JsonObject,
        people: MutableList<Person>,
        peopleBySourceId: Map<String, Person>
    ): Int {
        val prefs = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
        val entries = readList<MoodActivity.MoodEntry>(
            prefs.getString("mood_entries", "[]")
        ).toMutableList()

        val settings = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        val colors = listOf(
            settings.getString("mood_color_1", "#fffa94") ?: "#fffa94",
            settings.getString("mood_color_2", "#54bd44") ?: "#54bd44",
            settings.getString("mood_color_3", "#8844bd") ?: "#8844bd",
            settings.getString("mood_color_4", "#4446bd") ?: "#4446bd",
            settings.getString("mood_color_5", "#3a3a47") ?: "#3a3a47"
        )

        var imported = 0

        fun addMood(
            id: String,
            timestamp: Long,
            moodValue: String,
            note: String,
            refs: List<JsonElement>
        ) {
            if (timestamp <= 0L || entries.any { it.id == id }) return

            val score = moodScore(moodValue)
            val emoji = when (score) {
                5 -> "🤩"
                4 -> "😊"
                3 -> "😐"
                2 -> "😟"
                else -> "😢"
            }
            val memberIds = refs.mapNotNull { ref ->
                resolvePerson(ref, people, peopleBySourceId, createUnknown = false)?.id
            }
            val color = colors[5 - score].toColor(Color.GRAY)

            entries.add(
                MoodActivity.MoodEntry(
                    id = id,
                    timestamp = timestamp,
                    moodEmoji = emoji,
                    moodRotation = (score - 3) * 15f,
                    moodLabel = moodValue.ifBlank { score.toString() },
                    moodColor = color,
                    memberIds = memberIds,
                    note = note
                )
            )
            imported++
        }

        root.array("system_moods").forEach { source ->
            val id = source.string("id")
            if (id.isBlank()) return@forEach
            val ref = source.child("member")
            addMood(
                id = "pluralspace-mood-$id",
                timestamp = source.timestamp("started_at"),
                moodValue = source.string("mood"),
                note = "",
                refs = listOfNotNull(ref)
            )
        }

        root.array("journal_entries").forEach { source ->
            val mood = source.string("mood_label").ifBlank { source.string("mood_id") }
            if (mood.isBlank()) return@forEach
            val id = source.string("id")
            if (id.isBlank()) return@forEach

            addMood(
                id = "pluralspace-journal-mood-$id",
                timestamp = source.timestamp("created_at"),
                moodValue = mood,
                note = source.string("content"),
                refs = source.array("members").toList()
            )
        }

        prefs.edit().putString("mood_entries", gson.toJson(entries)).apply()
        return imported
    }

    private fun importChats(
        root: JsonObject,
        people: MutableList<Person>,
        peopleBySourceId: Map<String, Person>
    ): Pair<Int, Int> {
        val prefs = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
        val groups = readList<ChatGroup>(
            prefs.getString("sysmedia_chat_groups", "[]")
        ).toMutableList()
        val messages = readList<DirectMessage>(
            prefs.getString("sysmedia_dms", "[]")
        ).toMutableList()

        var channelCount = 0
        var messageCount = 0

        root.array("chat_channels").forEach { channel ->
            val channelId = channel.string("id")
            if (channelId.isBlank()) return@forEach

            val chatId = "pluralspace-chat-$channelId"
            val chat = groups.firstOrNull { it.id == chatId } ?: ChatGroup(
                id = chatId,
                name = channel.string("name").ifBlank { "Imported chat" }
            )

            chat.name = channel.string("name").ifBlank { chat.name }
            if (groups.none { it.id == chatId }) {
                groups.add(chat)
                channelCount++
            }

            channel.array("messages").forEach { sourceMessage ->
                val messageId = sourceMessage.string("id")
                if (messageId.isBlank() || messages.any { it.id == "pluralspace-message-$messageId" }) {
                    return@forEach
                }

                val senderName = sourceMessage.string("member_name").ifBlank { "Unknown" }
                val sender = findOrCreateChatAuthor(senderName, people, peopleBySourceId)
                if (!chat.participantIds.contains(sender.id)) {
                    chat.participantIds.add(sender.id)
                }

                messages.add(
                    DirectMessage(
                        id = "pluralspace-message-$messageId",
                        senderId = sender.id,
                        chatId = chatId,
                        content = sourceMessage.string("content"),
                        timestamp = sourceMessage.timestamp("created_at")
                            .takeIf { it > 0L } ?: System.currentTimeMillis()
                    )
                )
                messageCount++
            }
        }

        prefs.edit()
            .putString("sysmedia_chat_groups", gson.toJson(groups))
            .putString("sysmedia_dms", gson.toJson(messages))
            .apply()

        return channelCount to messageCount
    }

    private fun importPolls(
        root: JsonObject,
        people: MutableList<Person>,
        peopleBySourceId: Map<String, Person>
    ): Int {
        val prefs = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
        val posts = readList<SysmediaPost>(
            prefs.getString("sysmedia_posts", "[]")
        ).toMutableList()

        var imported = 0

        root.array("polls").forEach { source ->
            val pollId = source.string("id")
            if (pollId.isBlank()) return@forEach

            val postId = "pluralspace-poll-$pollId"
            if (posts.any { it.id == postId }) return@forEach

            val creatorRef = source.child("created_by_member")
            val creator = resolvePerson(
                creatorRef,
                people,
                peopleBySourceId,
                createUnknown = true
            ) ?: findOrCreateChatAuthor("PluralSpace", people, peopleBySourceId)

            val options = source.array("options")
            val optionTexts = options.map { it.string("text") }.toMutableList()
            val votes = mutableMapOf<String, Int>()

            options.forEachIndexed { optionIndex, option ->
                option.array("votes").forEach { vote ->
                    val voterName = vote.string("member_name")
                    val voter = people.firstOrNull { it.name.equals(voterName, true) }
                    if (voter != null) {
                        votes.putIfAbsent(voter.id, optionIndex)
                    }
                }
            }

            posts.add(
                SysmediaPost(
                    id = postId,
                    senderId = creator.id,
                    content = buildString {
                        append(source.string("title"))
                        source.string("description").takeIf { it.isNotBlank() }?.let {
                            append("\n\n")
                            append(it)
                        }
                    },
                    timestamp = source.timestamp("created_at")
                        .takeIf { it > 0L } ?: System.currentTimeMillis(),
                    poll = SysmediaPoll(
                        options = optionTexts,
                        votes = votes
                    )
                )
            )
            imported++
        }

        prefs.edit().putString("sysmedia_posts", gson.toJson(posts)).apply()
        return imported
    }

    private fun importRelationships(
        root: JsonObject,
        people: MutableList<Person>,
        peopleBySourceId: Map<String, Person>
    ): Int {
        val relationships = root.array("member_relationships")
        if (relationships.size() == 0) return 0

        val prefs = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
        val environments = readList<RelationEnvironment>(
            prefs.getString("relations_environments", "[]")
        ).toMutableList()

        val systemName = root.obj("system").string("name").ifBlank { "PluralSpace" }
        val environment = environments.firstOrNull { it.name == systemName }
            ?: RelationEnvironment(name = systemName).also { environments.add(it) }

        val typeNames = root.array("relationship_types")
            .associate { it.string("id") to it.string("name") }

        var imported = 0

        relationships.forEach { source ->
            val sourceId = source.string("id")
            if (sourceId.isBlank()) return@forEach
            val edgeId = "pluralspace-relation-$sourceId"
            if (environment.data.edges.any { it.id == edgeId }) return@forEach

            val left = resolvePerson(
                source.child("member"),
                people,
                peopleBySourceId,
                createUnknown = false
            ) ?: return@forEach
            val right = resolvePerson(
                source.child("related_member"),
                people,
                peopleBySourceId,
                createUnknown = false
            ) ?: return@forEach
            if (left.id == right.id) return@forEach

            val leftNode = ensureMemberNode(environment.data, left)
            val rightNode = ensureMemberNode(environment.data, right)
            val typeId = source.string("relationship_type")
            val typeName = typeNames[typeId]
                ?: source.string("relationship_type")
                    .ifBlank { "Relationship" }

            environment.data.edges.add(
                RelationEdge(
                    id = edgeId,
                    nodeIds = mutableListOf(leftNode.id, rightNode.id),
                    tag = typeName,
                    note = source.string("notes").takeIf { it.isNotBlank() }
                )
            )
            imported++
        }

        prefs.edit()
            .putString("relations_environments", gson.toJson(environments))
            .apply()

        return imported
    }

    private fun ensureMemberNode(data: RelationsData, person: Person): RelationNode {
        return data.nodes.firstOrNull { it.memberId == person.id }
            ?: RelationNode(
                id = "pluralspace-member-${person.id}",
                type = NodeType.MEMBER,
                name = person.name,
                color = person.profileColor,
                memberId = person.id,
                x = 500f + data.nodes.size * 40f,
                y = 500f + data.nodes.size * 30f
            ).also { data.nodes.add(it) }
    }

    private fun findOrCreateChatAuthor(
        name: String,
        people: MutableList<Person>,
        peopleBySourceId: Map<String, Person>
    ): Person {
        people.firstOrNull { it.name.equals(name, true) }?.let { return it }

        val manualId = "pluralspace-chat-author-${name.lowercase(Locale.ROOT)}"
        people.firstOrNull { it.manualId == manualId }?.let { return it }

        val author = Person(
            name = name,
            manualId = manualId,
            isSysmediaOnly = true,
            sysmediaProfile = SysmediaProfile(
                handle = name.lowercase(Locale.ROOT)
                    .replace(" ", "_")
                    .replace(Regex("[^a-z0-9_]"), "")
            )
        )
        people.add(author)
        return author
    }

    private fun resolvePerson(
        reference: JsonElement?,
        people: MutableList<Person>,
        peopleBySourceId: Map<String, Person>,
        createUnknown: Boolean
    ): Person? {
        if (reference == null || reference.isJsonNull) return null

        val sourceId = reference.asSourceId()
        peopleBySourceId[sourceId]?.let { return it }
        people.firstOrNull { it.manualId == sourceId }?.let { return it }
        people.firstOrNull { it.name.equals(sourceId, true) }?.let { return it }

        val name = if (reference.isJsonObject) {
            reference.asJsonObject.string("name")
                .ifBlank { reference.asJsonObject.string("display_name") }
        } else {
            sourceId
        }

        if (!createUnknown || name.isBlank()) return null
        return findOrCreateChatAuthor(name, people, peopleBySourceId)
    }

    private fun moodScore(value: String): Int {
        val raw = value.trim().lowercase(Locale.ROOT)
        raw.toIntOrNull()?.takeIf { it in 1..5 }?.let { return it }

        return when {
            "rad" in raw || "great" in raw || "excellent" in raw -> 5
            "good" in raw || "happy" in raw -> 4
            "meh" in raw || "neutral" in raw || "okay" in raw -> 3
            "bad" in raw || "sad" in raw -> 2
            "awful" in raw || "terrible" in raw -> 1
            else -> 3
        }
    }

    private fun normalizePath(path: String): String =
        path.replace('\\', '/').trimStart('/')

    private fun readListString(value: String?): List<String> =
        try {
            gson.fromJson(value ?: "[]", object : TypeToken<List<String>>() {}.type)
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

    private inline fun <reified T> readList(value: String?): List<T> =
        try {
            gson.fromJson(value ?: "[]", object : TypeToken<List<T>>() {}.type)
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

    private fun JsonObject.string(key: String): String {
        val value = get(key) ?: return ""
        if (value.isJsonNull) return ""
        return try {
            if (value.isJsonPrimitive) value.asString else ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun JsonObject.boolean(key: String): Boolean =
        try {
            get(key)?.takeIf { !it.isJsonNull }?.asBoolean ?: false
        } catch (_: Exception) {
            false
        }

    private fun JsonObject.array(key: String): JsonArray =
        try {
            get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
        } catch (_: Exception) {
            JsonArray()
        }

    private fun JsonObject.obj(key: String): JsonObject =
        try {
            get(key)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
        } catch (_: Exception) {
            JsonObject()
        }

    private fun JsonElement.string(key: String): String =
        try {
            takeIf { it.isJsonObject }?.asJsonObject?.string(key) ?: ""
        } catch (_: Exception) {
            ""
        }

    private fun JsonElement.array(key: String): JsonArray =
        try {
            takeIf { it.isJsonObject }?.asJsonObject?.array(key) ?: JsonArray()
        } catch (_: Exception) {
            JsonArray()
        }

    private fun JsonElement.boolean(key: String): Boolean =
        if (isJsonObject) asJsonObject.boolean(key) else false

    private fun JsonElement.stringArray(key: String): List<String> =
        if (isJsonObject) asJsonObject.stringArray(key) else emptyList()

    private fun JsonElement.timestamp(key: String): Long =
        if (isJsonObject) asJsonObject.timestamp(key) else 0L

    private fun JsonElement.child(key: String): JsonElement? =
        if (isJsonObject) asJsonObject.get(key) else null

    private fun JsonElement.asSourceId(): String {
        if (isJsonNull) return ""
        return try {
            if (isJsonObject) {
                asJsonObject.string("id")
                    .ifBlank { asJsonObject.string("member_id") }
                    .ifBlank { asJsonObject.string("name") }
            } else {
                asString
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun JsonObject.stringArray(key: String): List<String> =
        array(key).mapNotNull { item ->
            val value = item.asSourceId()
            value.takeIf { it.isNotBlank() }
        }

    private fun JsonArray.size(): Int = this.size()

    private fun JsonObject.timestamp(key: String): Long =
        get(key)?.toTimestamp() ?: 0L

    private fun JsonElement.toTimestamp(): Long {
        if (isJsonNull) return 0L

        try {
            if (isJsonPrimitive && asJsonPrimitive.isNumber) {
                val number = asLong
                return if (number in 1L..99_999_999_999L) number * 1000L else number
            }
        } catch (_: Exception) {
        }

        val text = try {
            asString
        } catch (_: Exception) {
            return 0L
        }

        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )

        for (pattern in patterns) {
            try {
                val formatter = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                formatter.parse(text)?.time?.let { return it }
            } catch (_: Exception) {
            }
        }
        return 0L
    }

    private fun String.toColor(fallback: Int): Int =
        try {
            if (isBlank()) fallback else Color.parseColor(this)
        } catch (_: Exception) {
            fallback
        }
}