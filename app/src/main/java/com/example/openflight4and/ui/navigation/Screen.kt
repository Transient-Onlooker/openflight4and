package com.example.openflight4and.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object NewFlight : Screen("new_flight")
    object BoardingPass : Screen("boarding_pass")
    object SeatSelection : Screen("seat_selection")
    object InFlight : Screen("in_flight")
    object History : Screen("history")
    object Trend : Screen("trend")
    object Settings : Screen("settings")
    object Sandbox : Screen("sandbox")
    object Tickets : Screen("tickets")
}
