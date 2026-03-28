package com.example.openflight4and.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.openflight4and.ui.theme.FlightPrimary
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun RulerPicker(
    modifier: Modifier = Modifier,
    minRequest: Int = 100,
    maxRequest: Int = 10000,
    step: Int = 100,
    initialValue: Int,
    onValueChange: (Int) -> Unit
) {
    val stepWidth = 12.dp
    val totalSteps = (maxRequest - minRequest) / step
    val startIndex = ((initialValue.coerceIn(minRequest, maxRequest) - minRequest) / step).coerceAtLeast(0)

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val snapBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    LaunchedEffect(listState, minRequest, maxRequest, step, onValueChange) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .map { index ->
                val value = minRequest + (index * step)
                value.coerceIn(minRequest, maxRequest)
            }
            .distinctUntilChanged()
            .collect { value ->
                onValueChange(value)
            }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
    ) {
        val contentInset = (maxWidth / 2) - (stepWidth / 2)

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            LazyRow(
                state = listState,
                flingBehavior = snapBehavior,
                contentPadding = PaddingValues(horizontal = contentInset),
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(totalSteps + 1) { index ->
                    val currentValue = minRequest + (index * step)
                    val isMajor = currentValue % 500 == 0 || currentValue == maxRequest
                    val isUnlimited = currentValue >= 6000

                    Box(
                        modifier = Modifier
                            .width(stepWidth)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(if (isMajor) 32.dp else 16.dp)
                            ) {
                                drawLine(
                                    color = if (isMajor) FlightPrimary else Color.Gray.copy(alpha = 0.5f),
                                    start = Offset(size.width / 2, 0f),
                                    end = Offset(size.width / 2, size.height),
                                    strokeWidth = if (isMajor) 4f else 2f
                                )
                            }

                            if (isMajor) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (isUnlimited && currentValue == maxRequest) "∞" else "$currentValue",
                                    color = if (isUnlimited && currentValue == maxRequest) Color(0xFFFF5252) else FlightPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Canvas(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = 4.dp, height = 40.dp)
            ) {
                drawLine(
                    color = Color(0xFFFF5252),
                    start = Offset(size.width / 2, 0f),
                    end = Offset(size.width / 2, size.height),
                    strokeWidth = 6f
                )
            }
        }
    }
}
