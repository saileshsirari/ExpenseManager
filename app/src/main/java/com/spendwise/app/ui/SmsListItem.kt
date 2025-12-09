package com.spendwise.app.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spendwise.feature.smsimport.data.SmsEntity

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun SmsListItem(
    sms: SmsEntity,
    onClick: (SmsEntity) -> Unit = {},
    onRequestMerchantFix: (SmsEntity) -> Unit = {},
    onMarkNotExpense: (SmsEntity, Boolean) -> Unit = { _, _ -> }
) {
    var expanded by remember { mutableStateOf(false) }

    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable {
                expanded = !expanded
                onClick(sms)
            },
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // ROW: Merchant + amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    sms.merchant ?: sms.sender,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    "₹${sms.amount.toInt()}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (sms.type.equals("credit", true))
                        Color(0xFF4CAF50)
                    else
                        Color(0xFFE57373)
                )
            }

            // Sub row: category + type
            Spacer(Modifier.height(4.dp))
            Text(
                "${sms.category} • ${sms.type?.uppercase()}",
                style = MaterialTheme.typography.labelMedium
            )

            // ———————————————
            // EXPANDED CONTENT
            // ———————————————
            if (expanded) {
                Spacer(Modifier.height(8.dp))

                Text(
                    text = sms.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.DarkGray
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        "Fix Merchant",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            onRequestMerchantFix(sms)
                        }
                    )

                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            onMarkNotExpense(sms, !sms.isIgnored)  // toggle on label tap
                        }
                    ) {
                        Text(
                            text = "Not Expense",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 6.dp)
                        )

                        Switch(
                            checked = sms.isIgnored,
                            onCheckedChange = { isChecked ->
                                onMarkNotExpense(sms, isChecked)
                            }
                        )
                    }
                }

            }
        }
    }
}

