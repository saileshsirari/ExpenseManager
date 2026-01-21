package com.spendwise.domain.com.spendwise.feature.smsimport.data

object SmsTemplateMatcher {

    fun matches(body: String, template: String): Boolean {
        val normalized = SmsTemplateUtil.buildTemplate(body)
        return normalized == template
    }
}
