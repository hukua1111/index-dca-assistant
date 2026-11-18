package com.indexdca.app

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow

/** 进程内全局状态 + SharedPreferences 持久化 */
object AppStore {
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    val holdings = MutableStateFlow<List<Holding>>(emptyList())
    val settings = MutableStateFlow(Settings())
    val indexCache = MutableStateFlow<List<IndexValuation>>(emptyList())

    fun init(context: Context) {
        prefs = context.getSharedPreferences("index_dca", Context.MODE_PRIVATE)
        holdings.value = prefs.getString(KEY_HOLDINGS, null)?.let {
            runCatching {
                gson.fromJson<List<Holding>>(it, object : TypeToken<List<Holding>>() {}.type)
            }.getOrNull()
        } ?: emptyList()
        settings.value = prefs.getString(KEY_SETTINGS, null)?.let {
            runCatching { gson.fromJson(it, Settings::class.java) }.getOrNull()
        } ?: Settings()
    }

    fun upsertHolding(h: Holding) {
        holdings.value = holdings.value.filterNot { it.id == h.id } + h
        persistHoldings()
    }

    fun removeHolding(id: String) {
        holdings.value = holdings.value.filterNot { it.id == id }
        persistHoldings()
    }

    fun saveSettings(s: Settings) {
        settings.value = s
        prefs.edit().putString(KEY_SETTINGS, gson.toJson(s)).apply()
    }

    fun setIndexes(list: List<IndexValuation>) {
        indexCache.value = list
    }

    private fun persistHoldings() {
        prefs.edit().putString(KEY_HOLDINGS, gson.toJson(holdings.value)).apply()
    }

    private const val KEY_HOLDINGS = "holdings"
    private const val KEY_SETTINGS = "settings"
}
