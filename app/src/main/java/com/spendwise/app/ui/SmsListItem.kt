package com.spendwise.app.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spendwise.feature.smsimport.data.SmsEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun SmsListItem(
    sms: SmsEntity,
    onClick: (SmsEntity) -> Unit,
    onRequestMerchantFix: (SmsEntity) -> Unit,
    onMarkNotExpense: (SmsEntity) -> Unit
) {
    val merchant = sms.merchant ?: sms.sender.orEmpty()
    val category = sms.category ?: "OTHER"
    val amountText = "₹${sms.amount.toInt()}" // assuming Double/Float

    val dateTime = Instant.ofEpochMilli(sms.timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()

    val dateStr = dateTime.format(DateTimeFormatter.ofPattern("dd MMM, HH:mm"))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick(sms) },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // Top row: Merchant + Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = merchant,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = amountText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.padding(top = 4.dp))

            // Second row: type + date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = sms.type ?: "",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.padding(top = 4.dp))

            // Actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onRequestMerchantFix(sms) }) {
                    Text("Fix Merchant")
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = { onMarkNotExpense(sms) }) {
                    Text("Not Expense")
                }
            }
        }
    }
}
