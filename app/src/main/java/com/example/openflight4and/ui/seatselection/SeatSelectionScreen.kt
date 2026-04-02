package com.example.openflight4and.ui.seatselection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openflight4and.R
import com.example.openflight4and.ui.components.FlightMapBackground
import com.example.openflight4and.ui.theme.FlightBlack
import com.example.openflight4and.ui.theme.FlightDarkGray
import com.example.openflight4and.ui.theme.FlightGray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeatSelectionScreen(
    onNavigateBack: () -> Unit,
    onSeatSelected: (String, String) -> Unit,
    onFinish: () -> Unit,
    hasTickets: Boolean,
    onTicketRequired: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val viewModel: SeatSelectionViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState()

    FlightMapBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FlightBlack.copy(alpha = 0.8f))
                ) {
                    TopAppBar(
                        title = {
                            Text(
                                stringResource(R.string.seatselection_title),
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back),
                                    tint = Color.White
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (isLandscape) 0.33f else 1f)
                                .widthIn(max = 280.dp)
                                .height(60.dp)
                                .clip(RoundedCornerShape(topStart = 100.dp, topEnd = 100.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .border(
                                    1.dp,
                                    Color.White.copy(alpha = 0.1f),
                                    RoundedCornerShape(topStart = 100.dp, topEnd = 100.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.seatselection_cockpit),
                                color = FlightGray,
                                style = MaterialTheme.typography.labelSmall,
                                letterSpacing = 4.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(if (isLandscape) 0.33f else 1f)
                                .widthIn(max = 280.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(modifier = Modifier.weight(2f), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Text("A", color = FlightGray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Text("B", color = FlightGray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Spacer(modifier = Modifier.size(1.dp))
                            }
                            Row(modifier = Modifier.weight(2f), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Text("C", color = FlightGray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Text("D", color = FlightGray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth(if (isLandscape) 0.33f else 1f)
                            .widthIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        items(40) { rowIndex ->
                            val rowNum = rowIndex + 1
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(modifier = Modifier.weight(2f), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    SeatIcon(rowNum, "A", uiState.selectedSeat) { viewModel.selectSeat("${rowNum}A") }
                                    SeatIcon(rowNum, "B", uiState.selectedSeat) { viewModel.selectSeat("${rowNum}B") }
                                }

                                Text(
                                    text = rowNum.toString(),
                                    color = FlightGray.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center
                                )

                                Row(modifier = Modifier.weight(2f), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    SeatIcon(rowNum, "C", uiState.selectedSeat) { viewModel.selectSeat("${rowNum}C") }
                                    SeatIcon(rowNum, "D", uiState.selectedSeat) { viewModel.selectSeat("${rowNum}D") }
                                }
                            }
                        }
                    }
                }

                if (uiState.selectedSeat != null && uiState.selectedCategory != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(if (isLandscape) 0.33f else 1f)
                            .widthIn(max = 280.dp)
                            .padding(vertical = 16.dp),
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
                                Text(
                                    stringResource(R.string.seatselection_selected_seat_format, uiState.selectedSeat ?: ""),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(uiState.selectedCategory!!, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                            }
                            Button(
                                onClick = {
                                    if (!hasTickets) {
                                        onTicketRequired()
                                        return@Button
                                    }
                                    onFinish()
                                },
                                modifier = Modifier.height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text(
                                    stringResource(R.string.seatselection_ready),
                                    color = FlightBlack,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        if (uiState.showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.hideBottomSheet() },
                sheetState = sheetState,
                containerColor = FlightDarkGray,
                contentColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .padding(24.dp)
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        stringResource(R.string.seatselection_choose_category),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    val categories = listOf(
                        stringResource(R.string.seatselection_category_focus),
                        stringResource(R.string.seatselection_category_workout),
                        stringResource(R.string.seatselection_category_meditation),
                        stringResource(R.string.seatselection_category_reading),
                        stringResource(R.string.seatselection_category_work),
                        stringResource(R.string.seatselection_category_other)
                    )

                    categories.chunked(3).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            row.forEach { category ->
                                CategoryCard(
                                    category = category,
                                    isSelected = uiState.selectedCategory == category,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        val selectedSeat = uiState.selectedSeat ?: return@CategoryCard
                                        viewModel.selectCategory(category)
                                        onSeatSelected(selectedSeat, category)
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
private fun SeatIcon(row: Int, letter: String, selectedSeat: String?, onClick: () -> Unit) {
    val seatId = "$row$letter"
    val isSelected = seatId == selectedSeat

    Icon(
        imageVector = Icons.Default.Chair,
        contentDescription = seatId,
        tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f),
        modifier = Modifier
            .size(42.dp)
            .clickable(onClick = onClick)
    )
}

@Composable
private fun CategoryCard(category: String, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = category, color = if (isSelected) FlightBlack else Color.White, fontWeight = FontWeight.Bold)
    }
}
