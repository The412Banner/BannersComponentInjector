package com.banner.inject.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ImportedGame(val name: String, val localId: String)

class ImportedGameRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("imported_games_prefs", Context.MODE_PRIVATE)
    private val KEY = "imported_games"

    fun getAll(): List<ImportedGame> {
        val json = prefs.getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val obj = arr.getJSONObject(it)
                ImportedGame(obj.getString("name"), obj.getString("localId"))
            }
        } catch (_: Exception) { emptyList() }
    }

    fun add(game: ImportedGame) {
        val list = getAll().toMutableList()
        list.removeAll { it.localId == game.localId }
        list.add(game)
        save(list)
    }

    fun remove(localId: String) {
        save(getAll().filter { it.localId != localId })
    }

    private fun save(list: List<ImportedGame>) {
        val arr = JSONArray()
        list.forEach { game ->
            arr.put(JSONObject().apply {
                put("name", game.name)
                put("localId", game.localId)
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }
}
