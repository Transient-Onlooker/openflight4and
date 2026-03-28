package com.example.openflight4and.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.openflight4and.ui.theme.FlightBlack
import com.example.openflight4and.ui.theme.FlightGray
import com.example.openflight4and.ui.theme.FlightOffWhite
import com.example.openflight4and.ui.theme.FlightPrimary

@Composable
fun FlightMapBackground(content: @Composable BoxScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(FlightBlack)) {
        // 배경에 은은한 레이더 그리드나 맵 텍스처를 넣을 수 있음
        // 지금은 깔끔한 다크 배경 유지
        content()
    }
}

data class MapOverlayPalette(
    val panelBackground: Color,
    val panelBorder: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accentText: Color,
    val iconTint: Color,
    val divider: Color,
    val trackColor: Color,
    val floatingButtonContainer: Color,
    val floatingButtonContent: Color
)

@Composable
fun rememberMapOverlayPalette(style: String): MapOverlayPalette {
    return when (style) {
        "light" -> MapOverlayPalette(
            panelBackground = Color.White.copy(alpha = 0.9f),
            panelBorder = Color.Black.copy(alpha = 0.12f),
            primaryText = FlightBlack,
            secondaryText = Color(0xFF262523).copy(alpha = 0.8f),
            accentText = FlightBlack,
            iconTint = FlightBlack,
            divider = Color.Black.copy(alpha = 0.08f),
            trackColor = Color.Black.copy(alpha = 0.08f),
            floatingButtonContainer = Color.White.copy(alpha = 0.96f),
            floatingButtonContent = FlightBlack
        )
        else -> MapOverlayPalette(
            panelBackground = Color.White.copy(alpha = 0.24f),
            panelBorder = Color.White.copy(alpha = 0.28f),
            primaryText = Color.White,
            secondaryText = FlightGray,
            accentText = FlightPrimary,
            iconTint = FlightGray,
            divider = Color.White.copy(alpha = 0.16f),
            trackColor = Color.White.copy(alpha = 0.16f),
            floatingButtonContainer = FlightBlack.copy(alpha = 0.88f),
            floatingButtonContent = FlightOffWhite
        )
    }
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White.copy(alpha = 0.15f),
    borderColor: Color = Color.White.copy(alpha = 0.2f),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(
                BorderStroke(1.dp, borderColor),
                RoundedCornerShape(16.dp)
            )
    ) {
        content()
    }
}

@Composable
fun PrimaryFlightButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false
) {
    val containerColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val contentColor = if (isDestructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
    
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = FlightGray.copy(alpha = 0.2f),
            disabledContentColor = FlightGray
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = FlightGray,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
    )
}
