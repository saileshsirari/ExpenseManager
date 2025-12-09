
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
    val amount: Double,
    val merchant: String?,
    val type: String?,
    val  category: String?,
    val isIgnored: Boolean = false
)
