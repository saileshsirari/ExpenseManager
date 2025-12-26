
package com.spendwise.feature.smsimport.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
@Entity(
    tableName = "sms",
    indices = [
        Index(value = ["sender", "timestamp", "body"], unique = true)
    ]
)
data class SmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val timestamp: Long,

    // Extracted data
    val amount: Double,
    val merchant: String?,
    val type: String?,          // debit/credit/etc (from ML)
    val category: String?,
    val showIgnored: Boolean = false,

    // 🔒 Ignore / override
    val isIgnored: Boolean = false,

    // 🔑 ADD THESE (new, safe)
    val ignoreReason: String? = null,
    val updatedAt: Long = 0L,

    // Linked-transfer fields
    val linkId: String? = null,
    val linkType: String? = null,         // INTERNAL_TRANSFER / POSSIBLE_TRANSFER
    val linkConfidence: Int = 0,
    val isNetZero: Boolean = false        // true when auto-excluded by linking
)


