package com.spendwise.app.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spendwise.core.R

@Composable
fun MerchantLogo(name: String) {
    val lower = name.lowercase()

    val iconRes = when {
        "zomato" in lower -> R.drawable.ic_zomato
        "swiggy" in lower -> R.drawable.ic_swiggy
        "amazon" in lower -> R.drawable.ic_amazon
        "flipkart" in lower -> R.drawable.ic_flipkart
        "uber" in lower -> R.drawable.ic_uber
        "ola" in lower -> R.drawable.ic_ola
        "rapido" in lower -> R.drawable.ic_ola
        "myntra" in lower -> R.drawable.ic_myntra
        "blinkit" in lower -> R.drawable.ic_ola
        "zepto" in lower -> R.drawable.ic_ola
        "domino" in lower -> R.drawable.ic_ola
        "pizza" in lower -> R.drawable.ic_ola

        // Person / UPI transfers
        else -> null
    }

    if (iconRes != null) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(36.dp)
        )
    } else {
        // PERSON avatar fallback
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color(0xFF1976D2), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
