package de.antonlorani.eudi.golfmembership.booking

data class BookingPageConfiguration(
    val headline: String,
    val segmentLabels: List<String>,
    val golfCourses: List<GolfCourse>,
    val tournaments: List<Tournament>,
    val primaryButtonLabel: String,
    val footerText: String,
)
