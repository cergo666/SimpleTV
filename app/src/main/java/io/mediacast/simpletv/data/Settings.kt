package io.mediacast.simpletv.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SharedPreferencesStore(private val p: SharedPreferences) : SettingsStore {
    override fun getString(key: String, def: String?): String? = p.getString(key, def)
    override fun putString(key: String, value: String?) {
        p.edit { if (value == null) remove(key) else putString(key, value) }
    }
    override fun getBoolean(key: String, def: Boolean): Boolean = p.getBoolean(key, def)
    override fun putBoolean(key: String, value: Boolean) { p.edit { putBoolean(key, value) } }
    override fun getInt(key: String, def: Int): Int = p.getInt(key, def)
    override fun putInt(key: String, value: Int) { p.edit { putInt(key, value) } }
    override fun getLong(key: String, def: Long): Long = p.getLong(key, def)
    override fun putLong(key: String, value: Long) { p.edit { putLong(key, value) } }
    override fun getStringSet(key: String, def: Set<String>): Set<String> = p.getStringSet(key, def) ?: def
    override fun putStringSet(key: String, value: Set<String>) { p.edit { putStringSet(key, value) } }
    override fun remove(key: String) { p.edit { remove(key) } }
    override fun allKeys(): Set<String> = p.all.keys
}

class AndroidSettings(context: Context) : Settings(SharedPreferencesStore(context.getSharedPreferences("stv", Context.MODE_PRIVATE))) {
    /** Режим интерфейса: auto, tv, phone. */
    var uiMode: String
        get() = store.getString("uiMode", "auto") ?: "auto"
        set(v) = store.putString("uiMode", v)
}
