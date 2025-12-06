
package com.spendwise.feature.smsimport

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SmsRaw(val sender: String, val body: String, val timestamp: Long)

interface SmsReader {
    suspend fun readAllSms(): List<SmsRaw>
}

class SmsReaderImpl(private val resolver: ContentResolver): SmsReader {
    override suspend fun readAllSms(): List<SmsRaw> = withContext(Dispatchers.IO) {
        val list = mutableListOf<SmsRaw>()
        val uri = Uri.parse("content://sms/inbox")
        val cursor: Cursor? = resolver.query(uri, arrayOf("address","body","date"), null, null, "date DESC")
        cursor?.use {
            while (it.moveToNext()) {
                val sender = it.getString(0) ?: ""
                val body = it.getString(1) ?: ""
                val timestamp = cursor.getLong(2).let { ts ->
                    if (ts < 10_000_000_000L) ts * 1000 else ts   // convert seconds → ms
                }
                list.add(SmsRaw(sender, body, timestamp))
            }
        }
        list
    }
}
