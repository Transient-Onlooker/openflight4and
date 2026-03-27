package com.example.openflight4and.ui.seatselection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.openflight4and.ui.components.FlightMapBackground
import com.example.openflight4and.ui.components.PrimaryFlightButton
import com.example.openflight4and.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeatSelectionScreen(
    onNavigateBack: () -> Unit,
    onSeatSelected: (String, String) -> Unit,
    onFinish: () -> Unit,
    hasTickets: Boolean,
    onTicketRequired: () -> Unit
) {
    var selectedSeat by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    FlightMapBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                // TopBar와 Cockpit Header를 하나의 Column으로 묶어 상단 고정
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FlightBlack.copy(alpha = 0.8f)) // 배경을 주어 스크롤되는 내용 위로 겹치지 않게 함
                ) {
                    TopAppBar(
                        title = { Text("좌석 및 집중 항목 선택", color = Color.White, fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기", tint = Color.White)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                    
                    // Cockpit Header (Fixed)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // 비행기 앞부분 형태 (배경과 자연스럽게 어우러지도록)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .clip(RoundedCornerShape(topStart = 100.dp, topEnd = 100.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(topStart = 100.dp, topEnd = 100.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "COCKPIT", 
                                color = FlightGray, 
                                style = MaterialTheme.typography.labelSmall, 
                                letterSpacing = 4.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    // Column Labels
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 36.dp)
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(2f), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Text("A", color = FlightGray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("B", color = FlightGray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Row(modifier = Modifier.weight(2f), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Text("C", color = FlightGray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("D", color = FlightGray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    // Divider
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp)
            ) {
                // Scrollable Seat Grid
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(40) { rowIndex ->
                        val rowNum = rowIndex + 1
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 왼쪽 2석
                            Row(modifier = Modifier.weight(2f), horizontalArrangement = Arrangement.SpaceEvenly) {
                                SeatIcon(rowNum, "A", selectedSeat) { selectedSeat = "${rowNum}A"; showBottomSheet = true }
                                SeatIcon(rowNum, "B", selectedSeat) { selectedSeat = "${rowNum}B"; showBottomSheet = true }
                            }

                            // 통로
                            Text(
                                text = rowNum.toString(),
                                color = FlightGray.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )

                            // 오른쪽 2석
                            Row(modifier = Modifier.weight(2f), horizontalArrangement = Arrangement.SpaceEvenly) {
                                SeatIcon(rowNum, "C", selectedSeat) { selectedSeat = "${rowNum}C"; showBottomSheet = true }
                                SeatIcon(rowNum, "D", selectedSeat) { selectedSeat = "${rowNum}D"; showBottomSheet = true }
                            }
                        }
                    }
                }

                // Bottom Action
                if (selectedSeat != null && selectedCategory != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("좌석 $selectedSeat", color = Color.White, fontWeight = FontWeight.Bold)
                                Text(selectedCategory!!, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                            }
                            Button(
                                onClick = {
                                    if (!hasTickets) {
                                        onTicketRequired()
                                        return@Button
                                    }

                                    // FlightStatusManager 초기화 후 서비스 시작
                                    // 실제 호출부(MainScreen)에서 onFinish를 호출하므로, 
                                    // 여기서 직접 서비스를 켜기보다 onFinish 이전에 실행하거나 MainScreen에서 처리하는 것이 좋음.
                                    // 요구사항대로 여기서 구현 시 context 필요.
                                    onFinish()
                                },
                                modifier = Modifier.height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("이륙 준비 완료", color = FlightBlack, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                } else {
                    // 선택 전에는 버튼 영역만큼 공간 확보하지 않아도 됨 (스크롤 공간 확보)
                }
            }
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                containerColor = FlightDarkGray,
                contentColor = Color.White
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("집중 항목을 선택하세요", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    
                    val categories = listOf("집중", "운동", "명상", "독서", "업무", "기타")
                    categories.chunked(3).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { category ->
                                CategoryCard(
                                    category = category,
                                    isSelected = selectedCategory == category,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        selectedCategory = category
                                        onSeatSelected(selectedSeat!!, category)
                                        showBottomSheet = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SeatIcon(row: Int, letter: String, selectedSeat: String?, onClick: () -> Unit) {
    val seatId = "$row$letter"
    val isSelected = seatId == selectedSeat
    
    Icon(
        imageVector = Icons.Default.Chair,
        contentDescription = seatId,
        tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f),
        modifier = Modifier.size(42.dp).clickable(onClick = onClick)
    )
}

@Composable
fun CategoryCard(category: String, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.height(56.dp).clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = category, color = if (isSelected) FlightBlack else Color.White, fontWeight = FontWeight.Bold)
    }
}
