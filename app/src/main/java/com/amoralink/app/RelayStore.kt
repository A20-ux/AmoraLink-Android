package com.amoralink.app

import android.content.Context

/**
 * 云端中继的本地注册状态。
 * device_id + secret 是设备轮询凭证，与只存哈希的 AI 配对令牌分开保存。
 */
object RelayStore {
    private const val PREFS = "amoralink_relay"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_SECRET = "secret"
    private const val KEY_LAST_POLL = "last_poll_at"
    private const val KEY_LAST_OK = "last_poll_ok"
    private const val KEY_LAST_MSG = "last_poll_msg"
    private const val KEY_PUSH_TOKEN = "push_token"

    fun save(context: Context, deviceId: String, secret: String) {
        if (deviceId.isBlank() || secret.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DEVICE_ID, deviceId)
            .putString(KEY_SECRET, secret)
            .apply()
    }

    fun getDeviceId(context: Context): String =
        prefs(context).getString(KEY_DEVICE_ID, "") ?: ""

    fun getSecret(context: Context): String =
        prefs(context).getString(KEY_SECRET, "") ?: ""

    fun savePushToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_PUSH_TOKEN, token).apply()
    }

    fun getPushToken(context: Context): String =
        prefs(context).getString(KEY_PUSH_TOKEN, "") ?: ""

    fun isRegistered(context: Context): Boolean =
        getDeviceId(context).isNotBlank() && getSecret(context).isNotBlank()

    fun setLastPoll(context: Context, ok: Boolean, message: String) {
        prefs(context).edit()
            .putLong(KEY_LAST_POLL, System.currentTimeMillis())
            .putBoolean(KEY_LAST_OK, ok)
            .putString(KEY_LAST_MSG, message)
            .apply()
    }

    fun getLastPollAt(context: Context): Long =
        prefs(context).getLong(KEY_LAST_POLL, 0L)

    fun isLastOk(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LAST_OK, false)

    fun getLastMsg(context: Context): String =
        prefs(context).getString(KEY_LAST_MSG, "") ?: ""

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
