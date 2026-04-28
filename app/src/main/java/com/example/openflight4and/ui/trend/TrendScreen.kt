package com.example.openflight4and.ui.trend

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openflight4and.R
import com.example.openflight4and.ui.components.FlightMapBackground
import com.example.openflight4and.ui.components.GlassPanel
import com.example.openflight4and.ui.theme.FlightGray
import com.example.openflight4and.utils.FlightUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSandbox: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: TrendViewModel = viewModel(
        factory = TrendViewModel.Factory(context.applicationContext as Application)
    )
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TrendEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                TrendEvent.NavigateToSandbox -> onNavigateToSandbox()
            }
        }
    }

    FlightMapBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.trend_title), color = Color.White, fontWeight = FontWeight.Bold) },
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
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.trend_summary_title),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.trend_summary_description),
                    color = FlightGray,
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(32.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCard(
                        label = stringResource(R.string.trend_total_flights),
                        value = stringResource(R.string.trend_total_flights_format, uiState.totalFlights),
                        modifier = Modifier.weight(1f).clickable { viewModel.onDebugTap() }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    StatCard(
                        label = stringResource(R.string.trend_visited_airports),
                        value = stringResource(R.string.trend_visited_airports_format, uiState.visitedAirportsCount),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    val distVal = FlightUtils.convertDistance(uiState.totalDistanceKm, uiState.unitSystem)
                    StatCard(
                        label = stringResource(R.string.trend_total_distance),
                        value = "$distVal ${uiState.unitSystem}",
                        modifier = Modifier.weight(1f).clickable { viewModel.onDistanceTap() }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    StatCard(
                        label = stringResource(R.string.trend_focus_time),
                        value = FlightUtils.formatDuration(context, uiState.totalFocusMinutes),
                        modifier = Modifier.weight(1f).clickable { viewModel.onDebugTap() }
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    GlassPanel(modifier = modifier) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, color = FlightGray, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
