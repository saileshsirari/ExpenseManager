
package com.spendwise.feature.smsimport.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.feature.smsimport.data.SmsEntity
import com.spendwise.feature.smsimport.repo.SmsRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
@HiltViewModel
class SmsImportViewModel @Inject constructor(private val repo: SmsRepositoryImpl): ViewModel() {
    private val _items = MutableStateFlow<List<SmsEntity>>(emptyList())
    val items: StateFlow<List<SmsEntity>> = _items

    fun importAll(resolverProvider: () -> android.content.ContentResolver) {
        viewModelScope.launch {
            repo.importAll(resolverProvider).collect {
                list -> _items.value = list
            }
        }
    }
}
