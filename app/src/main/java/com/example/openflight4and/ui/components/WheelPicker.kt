package com.example.openflight4and.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RangeWheelPicker(
    ranges: List<Int>, // 50, 100, ...
    initialIndex: Int = 2,
    onRangeSelected: (Int) -> Unit
) {
    // 무한 스크롤 효과를 위해 리스트를 반복 (앞뒤로 충분히)
    // 실제로는 유한하지만 사용자가 끝까지 갈 일이 거의 없도록 큰 수로 설정
    val listRepeatCount = 100
    val middleIndex = listRepeatCount / 2
    val startIndex = middleIndex * ranges.size + initialIndex
    
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // 현재 중앙에 있는 아이템 계산
    // firstVisibleItemIndex는 스크롤 상태에 따라 변하므로 derivedStateOf로 감지
    // 중앙 위치 감지를 위해 레이아웃 정보를 활용
    
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.visibleItemsInfo.isNotEmpty()) {
                val viewportCenter = layoutInfo.viewportEndOffset / 2
                val centerItem = layoutInfo.visibleItemsInfo.minByOrNull { 
                    abs((it.offset + it.size / 2) - viewportCenter) 
                }
                
                centerItem?.let {
                    val realIndex = it.index % ranges.size
                    onRangeSelected(ranges[realIndex])
                }
            }
        }
    }

    Box(
        modifier = Modifier.height(100.dp).width(300.dp),
        contentAlignment = Alignment.Center
    ) {
        LazyRow(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(horizontal = 130.dp), // 중앙 정렬을 위한 패딩
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(ranges.size * listRepeatCount) { index ->
                val realIndex = index % ranges.size
                val value = ranges[realIndex]
                val text = if (value >= 2000) "무제한" else "${value}km"
                
                Text(
                    text = text,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.alpha(0.7f) // 기본 투명도
                )
            }
        }
    }
}
