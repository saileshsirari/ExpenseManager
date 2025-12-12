package com.spendwise.feature.smsimport.prefs

import android.content.Context
import android.content.SharedPreferences

class SmsImportPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sms_import_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IMPORT_DONE = "import_done"
    }

    var importCompleted: Boolean
        get() = prefs.getBoolean(KEY_IMPORT_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_IMPORT_DONE, value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }
}
