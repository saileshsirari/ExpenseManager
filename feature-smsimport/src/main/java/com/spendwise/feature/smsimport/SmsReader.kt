
package com.spendwise.feature.smsimport

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import androidx.core.net.toUri
import com.spendwise.core.ml.RawSms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SmsRaw(val sender: String, val body: String, val timestamp: Long)

interface SmsReader {
    suspend fun readAllSms(): List<SmsRaw>
    fun readSince(timestamp: Long): List<RawSms>

    // 🔹 ADD THIS
    fun readBefore(timestamp: Long, limit: Int): List<RawSms>
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

   override fun readSince(timestamp: Long): List<RawSms> {
        val cursor = resolver.query(
            Uri.parse("content://sms"),
            arrayOf("_id", "address", "body", "date"),
            "date > ?",
            arrayOf(timestamp.toString()),
            "date ASC"
        ) ?: return emptyList()

        val list = mutableListOf<RawSms>()

        cursor.use {
            while (cursor.moveToNext()) {
                val sender = cursor.getString(1) ?: ""
                val body = cursor.getString(2) ?: ""
                val date = cursor.getLong(3)
                list.add(RawSms(sender, body, date))
            }
        }
        return list
    }

    override fun readBefore(timestamp: Long, limit: Int): List<RawSms> {
        val cursor = resolver.query(
            "content://sms".toUri(),
            arrayOf("_id", "address", "body", "date"),
            "date < ?",
            arrayOf(timestamp.toString()),
            "date DESC LIMIT $limit"
        ) ?: return emptyList()

        val list = mutableListOf<RawSms>()

        cursor.use {
            while (it.moveToNext()) {
                val sender = it.getString(1) ?: ""
                val body = it.getString(2) ?: ""
                val date = it.getLong(3)
                list.add(RawSms(sender, body, date))
            }
        }

        return list
    }



}
