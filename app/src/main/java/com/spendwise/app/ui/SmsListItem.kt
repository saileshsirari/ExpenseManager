package com.spendwise.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spendwise.feature.smsimport.data.SmsEntity

@Composable
fun SmsListItem(
    sms: SmsEntity,
    onClick: (SmsEntity) -> Unit,
    onRequestMerchantFix: (SmsEntity) -> Unit,
    onMarkNotExpense: (SmsEntity, Boolean) -> Unit
) {
    val isLinked = sms.linkId != null && sms.linkType == "INTERNAL_TRANSFER"
    val isPossible = sms.linkId != null && sms.linkType == "POSSIBLE_TRANSFER"

    val cardColor = when {
        isLinked -> Color(0xFFDFF7DF)     // light green
        isPossible -> Color(0xFFFFF3CD)   // light yellow
        else -> MaterialTheme.colorScheme.surface
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(sms) },
        colors = CardDefaults.elevatedCardColors(containerColor = cardColor),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {

        Column(Modifier.padding(14.dp)) {

            // 🔗 Top Tag
            if (isLinked || isPossible) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Icon(
                        imageVector = if (isLinked) Icons.Default.CompareArrows else Icons.Default.Warning,
                        contentDescription = "",
                        tint = if (isLinked) Color(0xFF2E7D32) else Color(0xFFB76E00)
                    )
                    Text(
                        text = if (isLinked) "Internal Transfer" else "Possible Transfer",
                        color = if (isLinked) Color(0xFF2E7D32) else Color(0xFFB76E00),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }

            // Main Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(Modifier.weight(1f)) {
                    Text(
                        sms.merchant ?: sms.sender,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        sms.body.take(60),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2
                    )
                }

                Text(
                    text = "₹${sms.amount.toInt()}",
                    color = if (sms.type.equals("DEBIT", true)) Color(0xFFD32F2F) else Color(0xFF1B5E20),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))

            // Footer actions
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {

                TextButton(onClick = { onRequestMerchantFix(sms) }) {
                    Icon(Icons.Default.Link, contentDescription = "")
                    Text("Fix Merchant", modifier = Modifier.padding(start = 4.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = sms.isIgnored,
                        onCheckedChange = { onMarkNotExpense(sms, it) }
                    )
                    Text("Not Expense")
                }
            }
        }
    }
}
