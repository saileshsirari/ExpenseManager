package com.spendwise.feature.smsimport.prefs

import android.content.Context
import android.content.SharedPreferences

class SmsImportPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sms_import_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IMPORT_DONE = "import_done"
        private const val KEY_RECENT_IMPORT_DONE = "recent_import_done"
        private const val KEY_OLDER_IMPORT_DONE = "older_import_done"

    }

    var recentImportCompleted : Boolean
    get() = prefs.getBoolean(KEY_RECENT_IMPORT_DONE, false)
    set(value) = prefs.edit().putBoolean(KEY_RECENT_IMPORT_DONE, value).apply()
    var olderImportCompleted : Boolean
        get() = prefs.getBoolean(KEY_OLDER_IMPORT_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_OLDER_IMPORT_DONE, value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }
}
