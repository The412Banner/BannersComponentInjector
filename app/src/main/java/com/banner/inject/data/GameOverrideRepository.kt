package com.banner.inject.data

import android.content.Context
import com.banner.inject.model.GameOverride
import org.json.JSONArray
import org.json.JSONObject

class GameOverrideRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("game_overrides", Context.MODE_PRIVATE)

    fun get(gameId: String): GameOverride? {
        val json = prefs.getString(key(gameId), null) ?: return null
        return try {
            val obj = JSONObject(json)
            GameOverride(
                gameId = gameId,
                customName = obj.optString("name", "").takeIf { it.isNotBlank() },
                customGenres = obj.optJSONArray("genres")?.let { arr ->
                    (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
                },
                customDescription = obj.optString("desc", "").takeIf { it.isNotBlank() },
                customReleaseYear = obj.optString("year", "").takeIf { it.isNotBlank() },
                customMetacriticScore = obj.optInt("meta", -1).takeIf { it > 0 },
                linkedSteamAppId = obj.optString("steamId", "").takeIf { it.isNotBlank() }
            )
        } catch (_: Exception) { null }
    }

    fun save(override: GameOverride) {
        val obj = JSONObject().apply {
            override.customName?.let { put("name", it) }
            override.customGenres?.let { genres ->
                put("genres", JSONArray().also { arr -> genres.forEach { arr.put(it) } })
            }
            override.customDescription?.let { put("desc", it) }
            override.customReleaseYear?.let { put("year", it) }
            override.customMetacriticScore?.let { put("meta", it) }
            override.linkedSteamAppId?.let { put("steamId", it) }
        }
        prefs.edit().putString(key(override.gameId), obj.toString()).apply()
    }

    fun clear(gameId: String) {
        prefs.edit().remove(key(gameId)).apply()
    }

    fun hasOverride(gameId: String): Boolean = prefs.contains(key(gameId))

    private fun key(gameId: String) = "override_$gameId"
}
