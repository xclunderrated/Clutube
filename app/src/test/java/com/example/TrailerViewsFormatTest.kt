package com.example

import com.example.data.youtube.formatTrailerCount
import com.example.data.youtube.formatTrailerViews
import com.example.data.youtube.formatTrailerViewsLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class TrailerViewsFormatTest {

    @Test
    fun unknown_counts_format_blank() {
        assertEquals("", formatTrailerCount(0L))
        assertEquals("", formatTrailerCount(-1L))
        assertEquals("", formatTrailerViews(0L))
        assertEquals("", formatTrailerViewsLabel(-5L))
    }

    @Test
    fun small_counts_have_no_suffix() {
        assertEquals("1", formatTrailerCount(1L))
        assertEquals("942", formatTrailerCount(942L))
        assertEquals("942 views", formatTrailerViews(942L))
    }

    @Test
    fun thousands_use_K_suffix() {
        assertEquals("1K", formatTrailerCount(1_000L))
        assertEquals("1.5K", formatTrailerCount(1_500L))
        assertEquals("12.4K", formatTrailerCount(12_400L))
    }

    @Test
    fun millions_use_M_suffix_with_trailer_label() {
        assertEquals("1.2M", formatTrailerCount(1_200_000L))
        assertEquals("1.2M trailer views", formatTrailerViewsLabel(1_200_000L))
        assertEquals("3.4M views", formatTrailerViews(3_400_000L))
    }

    @Test
    fun billions_use_B_suffix() {
        assertEquals("2.1B", formatTrailerCount(2_100_000_000L))
        assertEquals("2.1B trailer views", formatTrailerViewsLabel(2_100_000_000L))
    }

    @Test
    fun whole_numbers_drop_decimal() {
        assertEquals("2M", formatTrailerCount(2_000_000L))
        assertEquals("1B", formatTrailerCount(1_000_000_000L))
    }

    @Test
    fun rounding_overflow_promotes_suffix() {
        assertEquals("1M", formatTrailerCount(999_499L))
        assertEquals("1B", formatTrailerCount(999_499_999L))
        assertEquals("999K", formatTrailerCount(999_449L))
    }
}
