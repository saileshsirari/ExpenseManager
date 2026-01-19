package com.spendwise.feature.smsimport.repo

import com.spendwise.core.ml.CategoryType
import com.spendwise.core.ml.MerchantExtractorMl

object MerchantCategoryOverride {

    fun keyFor(merchant: String): String {
        val norm = MerchantExtractorMl.normalize(merchant)
        return "category:$norm"
    }

    fun encode(category: CategoryType): String =
        category.name

    fun decode(value: String?): CategoryType? =
        value?.let {
            runCatching { CategoryType.valueOf(it) }.getOrNull()
        }
}
