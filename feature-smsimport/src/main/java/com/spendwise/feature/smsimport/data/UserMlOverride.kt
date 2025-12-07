package com.spendwise.feature.smsimport.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class UserMlOverride(
    @PrimaryKey val key: String,      // "merchant:Zomato", "category:Uber"
    val value: String                 // replacement merchant/category
)
