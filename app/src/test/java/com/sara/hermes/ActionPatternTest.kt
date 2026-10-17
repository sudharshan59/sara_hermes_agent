package com.sara.hermes

import org.junit.Assert.*
import org.junit.Test

class ActionPatternTest {

    @Test
    fun testAlarmColonRegex() {
        val regex = Regex("""(\d{1,2}):(\d{2})""")
        val query1 = "set alarm for 7:30 am"
        val match1 = regex.find(query1)
        assertNotNull(match1)
        assertEquals("7", match1!!.groupValues[1])
        assertEquals("30", match1.groupValues[2])

        val query2 = "alarm 06:45 pm tomorrow"
        val match2 = regex.find(query2)
        assertNotNull(match2)
        assertEquals("06", match2!!.groupValues[1])
        assertEquals("45", match2.groupValues[2])
    }

    @Test
    fun testAlarmWordNumberRegex() {
        val regex = Regex("""\b(\d{1,2})\b""")
        val query = "set alarm tomorrow morning 7 clock"
        val matches = regex.findAll(query).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()
        assertTrue(matches.contains(7))
    }

    @Test
    fun testTimerDurationRegex() {
        val regex = Regex("""\b(\d+)\s*(minute|minutes|min|mins|second|seconds|sec|secs|\u0ba8\u0bbf\u0bae\u0bbf\u0b9f\u0bae\u0bcd|\u0ba8\u0bca\u0b9f\u0bbf)\b""")
        val q1 = "set timer for 10 minutes"
        val m1 = regex.find(q1)
        assertNotNull(m1)
        assertEquals("10", m1!!.groupValues[1])
        assertEquals("minutes", m1.groupValues[2])

        val q2 = "timer 45 seconds"
        val m2 = regex.find(q2)
        assertNotNull(m2)
        assertEquals("45", m2!!.groupValues[1])
        assertEquals("seconds", m2.groupValues[2])

        val q3 = "5 நிமிடம்"
        val m3 = regex.find(q3)
        assertNotNull(m3)
        assertEquals("5", m3!!.groupValues[1])
        assertEquals("நிமிடம்", m3.groupValues[2])
    }

    @Test
    fun testMediaControlsMatching() {
        val pauseQueries = listOf("pause", "stop music", "பாட்டை நிறுத்து", "pause pannu")
        for (q in pauseQueries) {
            val isPause = q == "pause" || q == "stop" || q.contains("pause music") || q.contains("stop music") ||
                    q.contains("பாட்டை நிறுத்து") || q.contains("pause pannu")
            assertTrue("Should match pause: $q", isPause)
        }

        val nextQueries = listOf("next song", "skip song", "அடுத்த பாடல்", "song mathu")
        for (q in nextQueries) {
            val isNext = q.contains("next song") || q.contains("skip song") || q.contains("அடுத்த பாடல்") || q.contains("song mathu")
            assertTrue("Should match next: $q", isNext)
        }
    }

    @Test
    fun testFlashlightMatching() {
        val onQueries = listOf("turn on flashlight", "torch on", "டார்ச் போடு", "flashlight podu")
        for (q in onQueries) {
            val isOn = q.contains("flashlight on") || q.contains("torch on") || q.contains("turn on flashlight") ||
                    q.contains("டார்ச் போடு") || q.contains("flashlight podu")
            assertTrue("Should match flashlight on: $q", isOn)
        }

        val offQueries = listOf("turn off flashlight", "torch off", "டார்ச் அணை", "flashlight off pannu")
        for (q in offQueries) {
            val isOff = q.contains("flashlight off") || q.contains("torch off") || q.contains("turn off flashlight") ||
                    q.contains("டார்ச் அணை") || q.contains("flashlight off pannu")
            assertTrue("Should match flashlight off: $q", isOff)
        }
    }

    @Test
    fun testWakeWordMatching() {
        val hotwords = listOf(
            "hey siri",
            "siri",
            "hello siri",
            "hi siri",
            "ok siri",
            "ஹே சிரி",
            "சிரி"
        )
        val inputs = listOf(
            "hey siri open youtube",
            "siri play some music",
            "ஹே சிரி அலாரம் வை",
            "hello siri",
            "ok siri what time is it"
        )
        for (input in inputs) {
            val matches = hotwords.any { input.contains(it) }
            assertTrue("Should detect hotword in: $input", matches)
        }
    }

    @Test
    fun testPhoneDialerDigitsExtraction() {
        val input = "call 9876543210"
        val digits = input.removePrefix("call ").filter { it.isDigit() || it == '+' }
        assertEquals("9876543210", digits)
        assertTrue(digits.length >= 3)
    }

    @Test
    fun testNullSafetySanitizer() {
        fun getSafeString(raw: String?): String {
            if (raw == null) return ""
            val trimmed = raw.trim()
            return if (trimmed.equals("null", ignoreCase = true)) "" else trimmed
        }

        assertEquals("", getSafeString(null))
        assertEquals("", getSafeString("null"))
        assertEquals("", getSafeString("NULL"))
        assertEquals("Hello", getSafeString("Hello"))
    }
}