package com.spendwise.app.ui

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spendwise.feature.smsimport.data.SmsEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SmsListItem(
    sms: SmsEntity,
    isExpanded: Boolean = false,
    onClick: (SmsEntity) -> Unit,
    onRequestMerchantFix: (SmsEntity) -> Unit,
    onMarkNotExpense: (SmsEntity, Boolean) -> Unit
) {

    val dateText = rememberFormattedDate(sms.timestamp)

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick(sms)
            }
            .animateContentSize(),  // 🔥 smooth expand / collapse,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {

        Column(Modifier.padding(14.dp)) {

            // ---------- TRANSFER TAG ----------


            // ---------- MAIN CONTENT ----------
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(Modifier.weight(1f)) {

                    Text(
                        sms.merchant ?: sms.sender,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )

                    // ⭐ DATE LINE (NEW)
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    // SMS preview
                    Text(
                        sms.body,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Text(
                    text = "₹${sms.amount.toInt()}",
                    color = if (sms.type.equals("DEBIT", true)) Color(0xFFD32F2F) else Color(
                        0xFF1B5E20
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(Modifier.height(10.dp))

            // ---------- FOOTER ----------
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {

                    if ( sms.linkType == "INTERNAL_TRANSFER") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CompareArrows ,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32)
                            )
                            Text(
                                text =  "Internal Transfer" ,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                }
            }
        }
    }
}

// ------------------------------------------------------
// Helper: Format timestamp to readable UI date
// ------------------------------------------------------
@Composable
private fun rememberFormattedDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
