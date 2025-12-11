package com.spendwise.feature.smsimport.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "linked_patterns")
data class LinkedPatternEntity(
    @PrimaryKey val pattern: String
)
