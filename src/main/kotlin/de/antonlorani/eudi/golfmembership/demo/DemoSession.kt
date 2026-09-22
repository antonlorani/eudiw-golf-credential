package de.antonlorani.eudi.golfmembership.demo

data class DemoSession(
    val id: String,
    val state: DemoState = DemoState.Booking,
)
