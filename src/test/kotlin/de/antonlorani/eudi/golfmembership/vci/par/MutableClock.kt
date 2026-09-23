package de.antonlorani.eudi.golfmembership.vci.par

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class MutableClock(
    private var currentInstant: Instant,
    private val currentZone: ZoneId,
) : Clock() {
    override fun getZone(): ZoneId = currentZone

    override fun withZone(zone: ZoneId): Clock = MutableClock(currentInstant, zone)

    override fun instant(): Instant = currentInstant

    fun advance(duration: Duration) {
        currentInstant = currentInstant.plus(duration)
    }
}
