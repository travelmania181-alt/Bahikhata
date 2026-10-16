package com.bahikhata

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

internal enum class EntryType {
    INCOME,
    EXPENSE,
}

internal data class LedgerEntry(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val type: EntryType,
    val category: String,
    val amountMinor: Long,
    val note: String,
)

internal object LedgerStore {
    private const val PREFERENCES = "bahikhata"
    private const val ENTRIES_KEY = "entries"
    private const val DRIVE_FILE_KEY = "drive_file_id"
    private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"

    fun readEntries(context: Context): List<LedgerEntry> {
        val json = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(ENTRIES_KEY, "[]") ?: "[]"
        val array = JSONArray(json)
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            LedgerEntry(
                id = item.getString("id"),
                date = LocalDate.parse(item.getString("date")),
                type = EntryType.valueOf(item.getString("type")),
                category = item.getString("category"),
                amountMinor = item.getLong("amountMinor"),
                note = item.getString("note"),
            )
        }
    }

    fun saveEntries(context: Context, entries: List<LedgerEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("date", entry.date.toString())
                    .put("type", entry.type.name)
                    .put("category", entry.category)
                    .put("amountMinor", entry.amountMinor)
                    .put("note", entry.note),
            )
        }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(ENTRIES_KEY, array.toString())
            .apply()
    }

    fun driveFileId(context: Context): String? =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(DRIVE_FILE_KEY, null)

    fun saveDriveFileId(context: Context, id: String) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(DRIVE_FILE_KEY, id)
            .apply()
    }

    fun driveScope(): String = DRIVE_SCOPE
}
