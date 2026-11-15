package com.amoralink.app

import android.content.Context

/**
 * 所有推送平台统一遵循的接口。
 * 目前只是接口定义，尚未连接实际推送服务。
 */
interface PushProvider {

    val providerName: String

    fun register(
        context: Context,
        callback: (Boolean, String?) -> Unit
    )

    fun unregister(
        context: Context,
        callback: (Boolean) -> Unit
    )
}

/**
 * 保存推送平台和设备令牌。
 */
object PushRegistrationStore {

    private const val PREFS = "amoralink_push_registration"
    private const val KEY_PROVIDER = "provider"
    private const val KEY_TOKEN = "device_token"
    private const val KEY_UPDATED = "updated_at"
    private const val KEY_REGISTERED = "registered"

    fun save(
        context: Context,
        provider: String,
        token: String
    ) {
        if (provider.isBlank() || token.isBlank()) return

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROVIDER, provider)
            .putString(KEY_TOKEN, token)
            .putLong(KEY_UPDATED, System.currentTimeMillis())
            .putBoolean(KEY_REGISTERED, true)
            .apply()
    }

    fun getProvider(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PROVIDER, "") ?: ""
    }

    fun getToken(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, "") ?: ""
    }

    fun isRegistered(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_REGISTERED, false)
    }

    fun getUpdatedAt(context: Context): Long {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_UPDATED, 0L)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
