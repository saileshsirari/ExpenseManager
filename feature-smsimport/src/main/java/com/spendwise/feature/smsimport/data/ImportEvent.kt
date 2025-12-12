package com.spendwise.feature.smsimport.data

sealed class ImportEvent {
    data class Progress(
        val total: Int,
        val processed: Int,
        val message: String = "Reading messages…"
    ) : ImportEvent()

    data class Finished(
        val list: List<SmsEntity>
    ) : ImportEvent()
}
