package de.antonlorani.eudi.golfmembership.demo

data class DemoSession(
    val id: String,
    val step: DemoStep,
    val selection: String? = null
)
