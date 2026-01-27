package com.spendwise.domain.com.spendwise.feature.smsimport.data

import java.security.MessageDigest

object RawHashUtil {

    fun compute(
        sender: String,
        body: String,
        timestamp: Long
    ): String {
        val normalized =
            sender.trim().lowercase() + "|" +
                    body.trim().lowercase() + "|" +
                    timestamp

        return normalized.sha256()
    }

    private fun String.sha256(): String {
        val bytes = MessageDigest
            .getInstance("SHA-256")
            .digest(this.toByteArray())

        return bytes.joinToString("") {
            "%02x".format(it)
        }
    }
}
