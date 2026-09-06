package com.twocents.mobile.ui.feed

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.JsonReader
import android.util.JsonWriter
import java.time.Instant

/** Persistent, process-safe corpus used by advanced search. */
internal object AdvancedSearchIndex {
    private var database: SearchDatabase? = null

    @Synchronized
    fun initialize(context: Context) {
        if (database == null) database = SearchDatabase(context.applicationContext)
    }

    @Synchronized
    fun merge(
        rawPosts: List<String>,
        postVotes: Map<String, Int> = emptyMap(),
        pollVotes: Map<String, Int> = emptyMap(),
        likertVotes: Map<String, Int> = emptyMap(),
        pickVotes: Map<String, String> = emptyMap(),
    ) {
        if (rawPosts.isEmpty()) return
        database?.writableDatabase?.let { db ->
            db.beginTransaction()
            try {
                rawPosts.forEach { payload ->
                    val post = runCatching { org.json.JSONObject(payload) }.getOrNull() ?: return@forEach
                    val uuid = post.optString("uuid")
                    val createdAt = post.optString("created_at")
                    if (uuid.isBlank() || createdAt.isBlank()) return@forEach
                    val base = ContentValues().apply {
                        put("uuid", uuid)
                        put("created_at", createdAt)
                        put("payload", payload)
                    }
                    // REPLACE deletes the old row and used to erase known vote state
                    // whenever a feed page omitted its optional vote arrays.
                    db.insertWithOnConflict("posts", null, base, SQLiteDatabase.CONFLICT_IGNORE)
                    db.update("posts", ContentValues().apply {
                        put("created_at", createdAt)
                        put("payload", payload)
                    }, "uuid = ?", arrayOf(uuid))
                    val statuses = ContentValues()
                    postVotes[uuid]?.let { statuses.put("post_vote", it) }
                    pollVotes[uuid]?.let { statuses.put("poll_vote", it) }
                    likertVotes[uuid]?.let { statuses.put("likert_vote", it) }
                    pickVotes[uuid]?.let { statuses.put("pick_vote", it) }
                    if (statuses.size() > 0) db.update("posts", statuses, "uuid = ?", arrayOf(uuid))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    @Synchronized
    fun statuses(from: Instant? = null, to: Instant? = null): AdvancedSearchStatuses {
        val db = database?.readableDatabase ?: return AdvancedSearchStatuses()
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        from?.let { clauses += "created_at >= ?"; args += it.toString() }
        to?.let { clauses += "created_at <= ?"; args += it.toString() }
        val posts = LinkedHashMap<String, Int>(); val polls = LinkedHashMap<String, Int>()
        val likerts = LinkedHashMap<String, Int>(); val picks = LinkedHashMap<String, String>()
        db.query("posts", arrayOf("uuid", "post_vote", "poll_vote", "likert_vote", "pick_vote"),
            clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND "), args.takeIf { it.isNotEmpty() }?.toTypedArray(), null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val uuid = cursor.getString(0)
                if (!cursor.isNull(1)) cursor.getInt(1).takeIf { it != 0 }?.let { posts[uuid] = it }
                if (!cursor.isNull(2)) cursor.getInt(2).takeIf { it >= 0 }?.let { polls[uuid] = it }
                if (!cursor.isNull(3)) cursor.getInt(3).takeIf { it >= 0 }?.let { likerts[uuid] = it }
                if (!cursor.isNull(4)) cursor.getString(4).takeIf(String::isNotBlank)?.let { picks[uuid] = it }
            }
        }
        return AdvancedSearchStatuses(posts, polls, likerts, picks)
    }

    @Synchronized
    fun hasCompleteStatuses(from: Instant, to: Instant): Boolean {
        val db = database?.readableDatabase ?: return false
        return db.rawQuery("SELECT COUNT(*) FROM posts WHERE created_at >= ? AND created_at <= ? AND post_vote IS NULL", arrayOf(from.toString(), to.toString()))
            .use { it.moveToFirst() && it.getInt(0) == 0 }
    }

    @Synchronized
    fun snapshot(from: Instant? = null, to: Instant? = null): List<FeedPost> {
        val db = database?.readableDatabase ?: return emptyList()
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        from?.let { clauses += "created_at >= ?"; args += it.toString() }
        to?.let { clauses += "created_at <= ?"; args += it.toString() }
        return db.query(
            "posts", arrayOf("payload"), clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND "),
            args.takeIf { it.isNotEmpty() }?.toTypedArray(), null, null, "created_at DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    runCatching { parseFeedPost(org.json.JSONObject(cursor.getString(0))) }.getOrNull()?.let(::add)
                }
            }
        }
    }

    @Synchronized fun count(): Int = database?.readableDatabase?.rawQuery("SELECT COUNT(*) FROM posts", null)?.use { if (it.moveToFirst()) it.getInt(0) else 0 } ?: 0
    @Synchronized fun newest(): Instant? = edge("MAX")
    @Synchronized fun oldest(): Instant? = edge("MIN")
    @Synchronized private fun edge(fn: String): Instant? = database?.readableDatabase
        ?.rawQuery("SELECT $fn(created_at) FROM posts", null)
        ?.use { if (it.moveToFirst()) runCatching { Instant.parse(it.getString(0)) }.getOrNull() else null }

    @Synchronized fun clear() { database?.writableDatabase?.delete("posts", null, null) }

    @Synchronized
    fun exportPayloads(): org.json.JSONArray {
        val output = org.json.JSONArray()
        database?.readableDatabase?.query("posts", arrayOf("payload"), null, null, null, null, "created_at ASC")?.use { cursor ->
            while (cursor.moveToNext()) runCatching { org.json.JSONObject(cursor.getString(0)) }.getOrNull()?.let(output::put)
        }
        return output
    }

    @Synchronized
    fun exportRows(): org.json.JSONArray {
        val output = org.json.JSONArray()
        database?.readableDatabase?.query("posts", arrayOf("payload", "post_vote", "poll_vote", "likert_vote", "pick_vote"), null, null, null, null, "created_at ASC")?.use { cursor ->
            while (cursor.moveToNext()) {
                val payload = runCatching { org.json.JSONObject(cursor.getString(0)) }.getOrNull() ?: continue
                output.put(org.json.JSONObject().put("payload", payload)
                    .put("post_vote", if (cursor.isNull(1)) org.json.JSONObject.NULL else cursor.getInt(1))
                    .put("poll_vote", if (cursor.isNull(2)) org.json.JSONObject.NULL else cursor.getInt(2))
                    .put("likert_vote", if (cursor.isNull(3)) org.json.JSONObject.NULL else cursor.getInt(3))
                    .put("pick_vote", if (cursor.isNull(4)) org.json.JSONObject.NULL else cursor.getString(4)))
            }
        }
        return output
    }

    /** Streams every indexed row directly from SQLite so export memory stays constant. */
    @Synchronized
    fun writeExport(writer: JsonWriter) {
        writer.beginArray()
        database?.readableDatabase?.query(
            "posts",
            arrayOf("payload", "post_vote", "poll_vote", "likert_vote", "pick_vote"),
            null, null, null, null,
            "created_at DESC",
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                writer.beginObject()
                writer.name("payload")
                // Payloads are already validated JSON in SQLite. Keeping that JSON
                // as a string avoids parsing and recursively allocating every post
                // a second time during export. importRows accepts both this compact
                // representation and backups created by earlier app versions.
                writer.value(cursor.getString(0))
                writer.name("post_vote"); if (cursor.isNull(1)) writer.nullValue() else writer.value(cursor.getInt(1).toLong())
                writer.name("poll_vote"); if (cursor.isNull(2)) writer.nullValue() else writer.value(cursor.getInt(2).toLong())
                writer.name("likert_vote"); if (cursor.isNull(3)) writer.nullValue() else writer.value(cursor.getInt(3).toLong())
                writer.name("pick_vote"); if (cursor.isNull(4)) writer.nullValue() else writer.value(cursor.getString(4))
                writer.endObject()
            }
        }
        writer.endArray()
    }

    /**
     * Imports a streamed backup in small transactions. This avoids materializing a
     * potentially tens-of-thousands-row search corpus as one JSONArray in memory.
     */
    @Synchronized
    fun importRows(reader: JsonReader) {
        val payloads = ArrayList<String>(500)
        val postVotes = HashMap<String, Int>()
        val pollVotes = HashMap<String, Int>()
        val likertVotes = HashMap<String, Int>()
        val pickVotes = HashMap<String, String>()

        fun flush() {
            if (payloads.isEmpty()) return
            merge(payloads, postVotes, pollVotes, likertVotes, pickVotes)
            payloads.clear(); postVotes.clear(); pollVotes.clear(); likertVotes.clear(); pickVotes.clear()
        }

        reader.beginArray()
        while (reader.hasNext()) {
            val row = reader.readJsonObject()
            val payload = when (val stored = row.opt("payload")) {
                is org.json.JSONObject -> stored
                is String -> runCatching { org.json.JSONObject(stored) }.getOrNull()
                else -> null
            } ?: row
            val raw = payload.toString()
            val uuid = payload.optString("uuid")
            if (uuid.isBlank()) continue
            payloads += raw
            if (row.has("post_vote") && !row.isNull("post_vote")) postVotes[uuid] = row.optInt("post_vote")
            if (row.has("poll_vote") && !row.isNull("poll_vote") && row.optInt("poll_vote", -1) >= 0) pollVotes[uuid] = row.optInt("poll_vote")
            if (row.has("likert_vote") && !row.isNull("likert_vote") && row.optInt("likert_vote", -1) >= 0) likertVotes[uuid] = row.optInt("likert_vote")
            row.optString("pick_vote").takeIf(String::isNotBlank)?.let { pickVotes[uuid] = it }
            if (payloads.size >= 500) flush()
        }
        reader.endArray()
        flush()
    }

    @Synchronized
    fun importPayloads(items: org.json.JSONArray) {
        merge(buildList { for (index in 0 until items.length()) items.optJSONObject(index)?.toString()?.let(::add) })
    }

    @Synchronized
    fun importRows(items: org.json.JSONArray) {
        val payloads = ArrayList<String>(); val postVotes = HashMap<String, Int>(); val pollVotes = HashMap<String, Int>()
        val likertVotes = HashMap<String, Int>(); val pickVotes = HashMap<String, String>()
        for (index in 0 until items.length()) {
            val row = items.optJSONObject(index) ?: continue
            val payload = when (val stored = row.opt("payload")) {
                is org.json.JSONObject -> stored
                is String -> runCatching { org.json.JSONObject(stored) }.getOrNull()
                else -> null
            } ?: row
            val uuid = payload.optString("uuid"); if (uuid.isBlank()) continue
            payloads += payload.toString()
            if (row.has("post_vote") && !row.isNull("post_vote")) postVotes[uuid] = row.optInt("post_vote")
            if (row.has("poll_vote") && !row.isNull("poll_vote") && row.optInt("poll_vote", -1) >= 0) pollVotes[uuid] = row.optInt("poll_vote")
            if (row.has("likert_vote") && !row.isNull("likert_vote") && row.optInt("likert_vote", -1) >= 0) likertVotes[uuid] = row.optInt("likert_vote")
            row.optString("pick_vote").takeIf(String::isNotBlank)?.let { pickVotes[uuid] = it }
        }
        merge(payloads, postVotes, pollVotes, likertVotes, pickVotes)
    }

    private class SearchDatabase(context: Context) : SQLiteOpenHelper(context, "advanced-search.db", null, 2) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE posts (uuid TEXT PRIMARY KEY NOT NULL, created_at TEXT NOT NULL, payload TEXT NOT NULL, post_vote INTEGER, poll_vote INTEGER, likert_vote INTEGER, pick_vote TEXT)")
            db.execSQL("CREATE INDEX posts_created_at ON posts(created_at DESC)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE posts ADD COLUMN post_vote INTEGER")
                db.execSQL("ALTER TABLE posts ADD COLUMN poll_vote INTEGER")
                db.execSQL("ALTER TABLE posts ADD COLUMN likert_vote INTEGER")
                db.execSQL("ALTER TABLE posts ADD COLUMN pick_vote TEXT")
            }
        }
    }
}

private fun JsonReader.readJsonObject(): org.json.JSONObject {
    val output = org.json.JSONObject()
    beginObject()
    while (hasNext()) output.put(nextName(), readJsonValue())
    endObject()
    return output
}

private fun JsonReader.readJsonValue(): Any? = when (peek()) {
    android.util.JsonToken.BEGIN_OBJECT -> readJsonObject()
    android.util.JsonToken.BEGIN_ARRAY -> org.json.JSONArray().also { array ->
        beginArray(); while (hasNext()) array.put(readJsonValue()); endArray()
    }
    android.util.JsonToken.STRING -> nextString()
    android.util.JsonToken.NUMBER -> nextString().let { number ->
        number.toLongOrNull() ?: number.toDoubleOrNull() ?: number
    }
    android.util.JsonToken.BOOLEAN -> nextBoolean()
    android.util.JsonToken.NULL -> { nextNull(); org.json.JSONObject.NULL }
    else -> { skipValue(); org.json.JSONObject.NULL }
}

internal data class AdvancedSearchStatuses(
    val postVotes: Map<String, Int> = emptyMap(),
    val pollVotes: Map<String, Int> = emptyMap(),
    val likertVotes: Map<String, Int> = emptyMap(),
    val pickVotes: Map<String, String> = emptyMap(),
)
