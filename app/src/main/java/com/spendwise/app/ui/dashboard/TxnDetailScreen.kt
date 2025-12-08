package com.spendwise.app.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spendwise.core.ml.ClassifiedTxn

@Composable
fun TxnDetailScreen(tx: ClassifiedTxn) {
    Column(Modifier.padding(16.dp)) {
        Text("Explanation", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        Text("Sender → ${tx.explanation.senderReason}")
        Spacer(Modifier.height(8.dp))

        Text("Intent → ${tx.explanation.intentReason}")
        Spacer(Modifier.height(8.dp))

        Text("Merchant → ${tx.explanation.merchantReason}")
        Spacer(Modifier.height(8.dp))

        Text("Category → ${tx.explanation.categoryReason}")
    }
}
