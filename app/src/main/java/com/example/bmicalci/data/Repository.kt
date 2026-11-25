package com.example.bmicalci.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bmi_health_pro")

/** Single source of truth for everything persisted on the device. */
class Repository(context: Context) {
    private val store = context.applicationContext.dataStore
    private val json = Json { ignoreUnknownKeys = true }

    private val profileKey = stringPreferencesKey("profile")
    private val waterKey = stringPreferencesKey("water_by_day")
    private val sessionsKey = stringPreferencesKey("sessions")

    private val waterSerializer = MapSerializer(String.serializer(), Int.serializer())
    private val sessionsSerializer = ListSerializer(SessionRecord.serializer())

    val profile: Flow<Profile?> = store.data.map { prefs ->
        prefs[profileKey]?.let { runCatching { json.decodeFromString(Profile.serializer(), it) }.getOrNull() }
    }

    val waterByDay: Flow<Map<String, Int>> = store.data.map { prefs ->
        prefs[waterKey]?.let { runCatching { json.decodeFromString(waterSerializer, it) }.getOrNull() } ?: emptyMap()
    }

    val sessions: Flow<List<SessionRecord>> = store.data.map { prefs ->
        prefs[sessionsKey]?.let { runCatching { json.decodeFromString(sessionsSerializer, it) }.getOrNull() } ?: emptyList()
    }

    suspend fun saveProfile(profile: Profile) {
        store.edit { it[profileKey] = json.encodeToString(Profile.serializer(), profile) }
    }

    suspend fun addWater(ml: Int, day: LocalDate = LocalDate.now()) {
        store.edit { prefs ->
            val current = prefs[waterKey]?.let { runCatching { json.decodeFromString(waterSerializer, it) }.getOrNull() } ?: emptyMap()
            val key = day.toString()
            // Keep only the last 30 days so the blob stays small.
            val updated = (current + (key to ((current[key] ?: 0) + ml)))
                .entries.sortedByDescending { it.key }.take(30).associate { it.key to it.value }
            prefs[waterKey] = json.encodeToString(waterSerializer, updated)
        }
    }

    suspend fun addSession(record: SessionRecord) {
        store.edit { prefs ->
            val current = prefs[sessionsKey]?.let { runCatching { json.decodeFromString(sessionsSerializer, it) }.getOrNull() } ?: emptyList()
            val updated = (current + record).sortedByDescending { it.timestamp }.take(100)
            prefs[sessionsKey] = json.encodeToString(sessionsSerializer, updated)
        }
    }
}
