package com.spendwise.feature.smsimport.data

sealed class ImportEvent {
    data class Progress(
        val total: Int,
        val processed: Int,
        val message: String
    ) : ImportEvent()

    data class Finished(val list: List<SmsEntity>) : ImportEvent()

    object RecentReady : ImportEvent()
    object OlderImportStarted : ImportEvent()
    object OlderImportFinished : ImportEvent()
    data class OlderImportTick(
        val processed: Int,
        val estimatedTotal: Int
    ) : ImportEvent()


}




