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

private const val SeatSelectionLandscapeWidthFraction = 0.33f
private val SeatSelectionHorizontalPadding = 24.dp
private val SeatSelectionTopHeaderBottomPadding = 16.dp
private val SeatSelectionCockpitHeight = 60.dp
private val SeatSelectionContentMaxWidth = 280.dp
private val SeatSelectionHeaderCornerRadius = 100.dp
private val SeatSelectionHeaderBackgroundAlpha = 0.05f
private val SeatSelectionHeaderBorderAlpha = 0.1f
private val SeatSelectionHeaderLetterSpacing = 4.sp
private val SeatSelectionColumnBottomPadding = 8.dp
private val SeatSelectionListItemSpacing = 16.dp
private val SeatSelectionCardVerticalPadding = 16.dp
private val SeatSelectionCardInnerPadding = 16.dp
private val SeatSelectionActionButtonHeight = 48.dp
private val SeatSelectionActionButtonCornerRadius = 8.dp
private val SeatSelectionModalMaxWidth = 560.dp
private val SeatSelectionModalHorizontalPadding = 24.dp
private val SeatSelectionModalBottomPadding = 32.dp
private val SeatSelectionModalSpacing = 16.dp
private val SeatSelectionModalRowSpacing = 12.dp
private val SeatSelectionSeatIconSize = 42.dp
private val SeatSelectionSeatLabelFontSize = 14.sp
private val SeatSelectionSeatNumberAlpha = 0.5f
private val SeatSelectionUnselectedSeatAlpha = 0.2f
private val SeatSelectionCategoryHeight = 56.dp
private val SeatSelectionCategoryCornerRadius = 12.dp
private val SeatSelectionCategoryUnselectedAlpha = 0.05f
private val SeatSelectionThinDividerWidth = 1.dp
private val SeatSelectionAisleSpacerSize = 1.dp
private val SeatSelectionHeaderDividerAlpha = 0.1f
private val SeatSelectionContentHorizontalPadding = 24.dp
private val SeatSelectionSelectedCardBackgroundAlpha = 0.1f
private val SeatSelectionSelectedCategoryFontSize = 14.sp

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
                            .padding(horizontal = SeatSelectionHorizontalPadding)
                            .padding(bottom = SeatSelectionTopHeaderBottomPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (isLandscape) SeatSelectionLandscapeWidthFraction else 1f)
                                .widthIn(max = SeatSelectionContentMaxWidth)
                                .height(SeatSelectionCockpitHeight)
                                .clip(RoundedCornerShape(topStart = SeatSelectionHeaderCornerRadius, topEnd = SeatSelectionHeaderCornerRadius))
                                .background(Color.White.copy(alpha = SeatSelectionHeaderBackgroundAlpha))
                                .border(
                                    SeatSelectionThinDividerWidth,
                                    Color.White.copy(alpha = SeatSelectionHeaderBorderAlpha),
                                    RoundedCornerShape(topStart = SeatSelectionHeaderCornerRadius, topEnd = SeatSelectionHeaderCornerRadius)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.seatselection_cockpit),
                                color = FlightGray,
                                style = MaterialTheme.typography.labelSmall,
                                letterSpacing = SeatSelectionHeaderLetterSpacing,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = SeatSelectionColumnBottomPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(if (isLandscape) SeatSelectionLandscapeWidthFraction else 1f)
                                .widthIn(max = SeatSelectionContentMaxWidth),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(modifier = Modifier.weight(2f), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Text("A", color = FlightGray, fontSize = SeatSelectionSeatLabelFontSize, fontWeight = FontWeight.Bold)
                                }
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Text("B", color = FlightGray, fontSize = SeatSelectionSeatLabelFontSize, fontWeight = FontWeight.Bold)
                                }
                            }
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Spacer(modifier = Modifier.size(SeatSelectionAisleSpacerSize))
                            }
                            Row(modifier = Modifier.weight(2f), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Text("C", color = FlightGray, fontSize = SeatSelectionSeatLabelFontSize, fontWeight = FontWeight.Bold)
                                }
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Text("D", color = FlightGray, fontSize = SeatSelectionSeatLabelFontSize, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = SeatSelectionHeaderDividerAlpha))
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = SeatSelectionContentHorizontalPadding),
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
                            .fillMaxWidth(if (isLandscape) SeatSelectionLandscapeWidthFraction else 1f)
                            .widthIn(max = SeatSelectionContentMaxWidth),
                        verticalArrangement = Arrangement.spacedBy(SeatSelectionListItemSpacing),
                        contentPadding = PaddingValues(vertical = SeatSelectionCardVerticalPadding)
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
                                    color = FlightGray.copy(alpha = SeatSelectionSeatNumberAlpha),
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
                            .fillMaxWidth(if (isLandscape) SeatSelectionLandscapeWidthFraction else 1f)
                            .widthIn(max = SeatSelectionContentMaxWidth)
                            .padding(vertical = SeatSelectionCardVerticalPadding),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = SeatSelectionSelectedCardBackgroundAlpha),
                        shape = RoundedCornerShape(SeatSelectionCardInnerPadding),
                        border = androidx.compose.foundation.BorderStroke(SeatSelectionThinDividerWidth, MaterialTheme.colorScheme.primary)
                    ) {
                        Row(
                            modifier = Modifier.padding(SeatSelectionCardInnerPadding),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.seatselection_selected_seat_format, uiState.selectedSeat ?: ""),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    uiState.selectedCategory!!,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = SeatSelectionSelectedCategoryFontSize
                                )
                            }
                            Button(
                                onClick = {
                                    if (!hasTickets) {
                                        onTicketRequired()
                                        return@Button
                                    }
                                    onFinish()
                                },
                                modifier = Modifier.height(SeatSelectionActionButtonHeight),
                                shape = RoundedCornerShape(SeatSelectionActionButtonCornerRadius),
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
                        .widthIn(max = SeatSelectionModalMaxWidth)
                        .padding(SeatSelectionModalHorizontalPadding)
                        .padding(bottom = SeatSelectionModalBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(SeatSelectionModalSpacing)
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
                            horizontalArrangement = Arrangement.spacedBy(SeatSelectionModalRowSpacing)
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
        tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = SeatSelectionUnselectedSeatAlpha),
        modifier = Modifier
            .size(SeatSelectionSeatIconSize)
            .clickable(onClick = onClick)
    )
}

@Composable
private fun CategoryCard(category: String, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(SeatSelectionCategoryHeight)
            .clip(RoundedCornerShape(SeatSelectionCategoryCornerRadius))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = SeatSelectionCategoryUnselectedAlpha))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = category, color = if (isSelected) FlightBlack else Color.White, fontWeight = FontWeight.Bold)
    }
}
