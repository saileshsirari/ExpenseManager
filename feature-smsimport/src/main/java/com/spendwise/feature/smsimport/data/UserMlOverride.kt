package com.spendwise.feature.smsimport.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_ml_override")
data class UserMlOverride(
    @PrimaryKey val key: String,
    val value: String
)

