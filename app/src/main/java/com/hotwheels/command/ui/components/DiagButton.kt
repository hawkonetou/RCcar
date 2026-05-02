package com.hotwheels.command.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hotwheels.command.ui.theme.AccentElectric
import com.hotwheels.command.ui.theme.BgPanel
import com.hotwheels.command.ui.theme.CyanDim35
import com.hotwheels.command.ui.theme.MonoFamily

@Composable
fun DiagButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(28.dp)
            .border(1.dp, CyanDim35, RoundedCornerShape(5.dp))
            .background(BgPanel, RoundedCornerShape(5.dp))
            .clickable { onClick() }
    ) {
        Text(
            text = "⌖",
            color = AccentElectric.copy(alpha = 0.7f),
            fontFamily = MonoFamily,
            fontSize = 14.sp
        )
    }
}
