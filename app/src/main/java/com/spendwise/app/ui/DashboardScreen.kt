
package com.spendwise.app.ui
import android.Manifest
import android.content.pm.PackageManager
import android.icu.text.SimpleDateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.spendwise.feature.smsimport.data.SmsEntity
import com.spendwise.feature.smsimport.ui.SmsImportViewModel
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(viewModel: SmsImportViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val hasPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_SMS
    ) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.importAll{
                context.contentResolver
            }
        }
    }
    val items = viewModel.items.collectAsState().value

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        item {
            Text("This Month — Summary", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text("Transactions: ${items.size}")
            Spacer(Modifier.height(12.dp))
        }

        items(
            items = items,
            key = { it.id }  // required for stable expansion state
        ) { tx ->
            SmsListItem(tx = tx)
        }
    }

}

@Composable
fun SmsListItem(
    tx: SmsEntity
) {
    var expanded by rememberSaveable(tx.id) { mutableStateOf(false) }

    val isCredit = tx.type?.equals("credit", ignoreCase = true) == true
    val amountColor = if (isCredit) Color(0xFF2E7D32) else Color(0xFFC62828)

    val dateFormatted = remember(tx.timestamp) {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        sdf.format(Date(tx.timestamp))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 12.dp)
    ) {

        // MAIN ROW ----------------------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = tx.merchant ?: tx.sender,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = dateFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Text(
                text = (if (isCredit) "+₹" else "₹") + tx.amount,
                style = MaterialTheme.typography.titleMedium,
                color = amountColor,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(8.dp))

        // CATEGORY CHIP ------------------------------------------------------------------
        tx.type?.let {
            AssistChip(
                onClick = {},
                label = { Text(it.replaceFirstChar(Char::uppercase)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color(0xFFEDE7F6)
                )
            )
        }

        // EXPANDABLE SECTION --------------------------------------------------------------
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 10.dp)) {

                Text(
                    text = "Original SMS:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF7F7F7), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = tx.body,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Divider(thickness = 1.dp, color = Color(0xFFE0E0E0))
    }
}




