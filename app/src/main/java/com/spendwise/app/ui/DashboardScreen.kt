
package com.spendwise.app.ui
import android.Manifest
import android.content.pm.PackageManager
import android.icu.text.SimpleDateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.core.MerchantClassifier
import com.spendwise.core.R
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

    val classify = remember(tx.merchant, tx.body) {
        MerchantClassifier.classify(tx.merchant ?: tx.sender, tx.body)
    }

    val category = classify.category
    val logoRes = classify.logoRes
    val merchantName = classify.merchantName

    val isCredit = tx.type?.equals("credit", true) == true
    val amountColor = if (isCredit) Color(0xFF2E7D32) else Color(0xFFC62828)

    val dateFormatted = remember(tx.timestamp) {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            .format(Date(tx.timestamp))
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 12.dp)
    ) {

        // ROW WITH LOGO + MERCHANT + AMOUNT ------------------------------------------------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = logoRes),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )

                Spacer(Modifier.width(12.dp))

                Column {
                    Text(merchantName, style = MaterialTheme.typography.titleMedium)
                    Text(dateFormatted, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }

            Text(
                text = (if (isCredit) "+₹" else "₹") + tx.amount,
                color = amountColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(6.dp))

        // CATEGORY CHIP ---------------------------------------------------------------------
        AssistChip(
            onClick = {},
            label = { Text(category) },
            leadingIcon = {
                Icon(
                    painterResource(R.drawable.ic_category),
                    contentDescription = null
                )
            }
        )

        // EXPANDED SMS BODY -----------------------------------------------------------------
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(top = 10.dp)) {
                Text("Original SMS:", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))

                Box(
                    Modifier
                        .background(Color(0xFFF6F6F6), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(tx.body, lineHeight = 18.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Divider()
    }
}





