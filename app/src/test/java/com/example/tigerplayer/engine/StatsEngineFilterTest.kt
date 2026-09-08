package com.example.tigerplayer.engine

import com.example.tigerplayer.data.repository.HistoryRepository
import io.mockk.mockk
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsEngineFilterTest {

    private val engine = StatsEngine(mockk<HistoryRepository>(relaxed = true)) { 0L }

    @Test
    fun all_filters_map_to_expected_calendar_boundaries() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        val todayStart = invokeStartTime("Today")
        val thisWeekStart = invokeStartTime("This Week")
        val last7DaysStart = invokeStartTime("Last 7 Days")
        val thisMonthStart = invokeStartTime("This Month")
        val last30DaysStart = invokeStartTime("Last 30 Days")
        val last90DaysStart = invokeStartTime("Last 90 Days")
        val thisYearStart = invokeStartTime("This Year")
        val lifetimeStart = invokeStartTime("Lifetime")

        assertEquals(today, toLocalDate(todayStart, zone))
        assertEquals(expectedWeekStart(today), toLocalDate(thisWeekStart, zone))
        assertEquals(today.minusDays(6), toLocalDate(last7DaysStart, zone))
        assertEquals(today.withDayOfMonth(1), toLocalDate(thisMonthStart, zone))
        assertEquals(today.minusDays(29), toLocalDate(last30DaysStart, zone))
        assertEquals(today.minusDays(89), toLocalDate(last90DaysStart, zone))
        assertEquals(today.withDayOfYear(1), toLocalDate(thisYearStart, zone))
        assertEquals(0L, lifetimeStart)
    }

    @Test
    fun non_lifetime_filters_resolve_to_midnight_and_never_fallback_to_zero() {
        val zone = ZoneId.systemDefault()
        val filters = listOf(
            "Today",
            "This Week",
            "Last 7 Days",
            "This Month",
            "Last 30 Days",
            "Last 90 Days",
            "This Year"
        )

        filters.forEach { filter ->
            val startTime = invokeStartTime(filter)
            assertTrue("$filter should not map to lifetime (0L)", startTime > 0L)
            assertEquals("$filter should start at local midnight", LocalTime.MIDNIGHT, toLocalTime(startTime, zone))
        }
    }

    private fun invokeStartTime(filter: String): Long {
        val method = StatsEngine::class.java.getDeclaredMethod(
            "calculateStartTimeForFilter",
            String::class.java
        )
        method.isAccessible = true
        return method.invoke(engine, filter) as Long
    }

    private fun expectedWeekStart(today: LocalDate): LocalDate {
        val firstDayOfWeek = when (Calendar.getInstance().firstDayOfWeek) {
            Calendar.MONDAY -> DayOfWeek.MONDAY
            Calendar.TUESDAY -> DayOfWeek.TUESDAY
            Calendar.WEDNESDAY -> DayOfWeek.WEDNESDAY
            Calendar.THURSDAY -> DayOfWeek.THURSDAY
            Calendar.FRIDAY -> DayOfWeek.FRIDAY
            Calendar.SATURDAY -> DayOfWeek.SATURDAY
            else -> DayOfWeek.SUNDAY
        }
        return today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    }

    private fun toLocalDate(millis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private fun toLocalTime(millis: Long, zone: ZoneId): LocalTime =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
}

