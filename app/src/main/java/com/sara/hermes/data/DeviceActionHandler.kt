package com.sara.hermes.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent

class DeviceActionHandler(private val context: Context) {

    sealed class ActionResult {
        data class Handled(val speechResponse: String, val action: () -> Unit) : ActionResult()
        object NotHandled : ActionResult()
    }

    fun tryExecute(query: String): ActionResult {
        val raw = query.trim().lowercase()
        val q = raw.replace(Regex("[.,!?]"), "").trim()
        val isTamil = q.any { it in '\u0B80'..'\u0BFF' } || 
                      q.contains("pannu") || q.contains("podu") || q.contains("thira") || q.contains("mathu") || q.contains("niruthu")

        // ========================================== 
        // 1. Universal Media Playback Controls (YouTube, Spotify, Music Players)
        // ========================================== 
        // A. Pause / Stop
        if (q == "pause" || q == "stop" || q.contains("pause music") || q.contains("stop music") || 
            q.contains("pause video") || q.contains("pause song") || q.contains("நிறுத்து") || 
            q.contains("பாட்டை நிறுத்து") || q.contains("pause pannu") || q.contains("stop pannu")) {
            val speech = if (isTamil) "இசை நிறுத்தப்பட்டது" else "Pausing music"
            return ActionResult.Handled(speech) {
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE)
            }
        }

        // B. Play / Resume
        if (q == "play" || q == "resume" || q.contains("play music") || q.contains("resume music") || 
            q.contains("continue") || q.contains("தொடங்கு") || q.contains("பாட்டை போடு") || 
            q.contains("play pannu") || q.contains("paatu podu") || q.contains("start pannu")) {
            val speech = if (isTamil) "இசை தொடங்குகிறது" else "Playing music"
            return ActionResult.Handled(speech) {
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY)
            }
        }

        // C. Next Song / Skip / Change Song
        if (q.contains("next song") || q.contains("next track") || q.contains("skip song") || 
            q.contains("change song") || q.contains("அடுத்த பாடல்") || q.contains("அடுத்த பாட்டு") || 
            q.contains("song mathu") || q.contains("next track")) {
            val speech = if (isTamil) "அடுத்த பாடல்" else "Skipping to next song"
            return ActionResult.Handled(speech) {
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT)
            }
        }

        // D. Previous Song
        if (q.contains("previous song") || q.contains("previous track") || q.contains("last song") || 
            q.contains("முந்தைய பாடல்") || q.contains("முந்தைய பாட்டு")) {
            val speech = if (isTamil) "முந்தைய பாடல்" else "Playing previous song"
            return ActionResult.Handled(speech) {
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
        }

        // ========================================== 
        // 2. Close App / Go Home
        // ========================================== 
        if (q == "close app" || q == "close" || q == "go home" || q.contains("go to home") || 
            q.contains("home screen") || q.contains("close pannu") || q.contains("வெளியே போ") || 
            q.contains("மூடு") || q.contains("முகப்பு")) {
            val speech = if (isTamil) "முகப்பு திரைக்கு செல்கிறது" else "Going to home screen"
            return ActionResult.Handled(speech) {
                goToHomeScreen()
            }
        }

        // ========================================== 
        // 3. Flashlight / Torch Control
        // ========================================== 
        if (q.contains("flashlight on") || q.contains("torch on") || q.contains("turn on flashlight") || 
            q.contains("turn on torch") || q.contains("turn on the flashlight") || 
            q.contains("டார்ச் போடு") || q.contains("டார்ச் ஆன்") || q.contains("flashlight podu")) {
            val speech = if (isTamil) "டார்ச் ஆன் செய்யப்படுகிறது" else "Turning on flashlight"
            return ActionResult.Handled(speech) {
                setFlashlight(true)
            }
        }
        if (q.contains("flashlight off") || q.contains("torch off") || q.contains("turn off flashlight") || 
            q.contains("turn off torch") || q.contains("turn off the flashlight") || 
            q.contains("டார்ச் நிறுத்து") || q.contains("டார்ச் ஆப்") || q.contains("flashlight off pannu")) {
            val speech = if (isTamil) "டார்ச் அணைக்கப்படுகிறது" else "Turning off flashlight"
            return ActionResult.Handled(speech) {
                setFlashlight(false)
            }
        }

        // ========================================== 
        // 4. Volume Controls
        // ========================================== 
        if (q == "mute" || q.contains("mute volume") || q.contains("silent volume") || q.contains("சத்தம் நிறுத்து")) {
            val speech = if (isTamil) "சத்தம் அணைக்கப்படுகிறது" else "Muting volume"
            return ActionResult.Handled(speech) {
                setVolumeMute()
            }
        }
        if (q.contains("max volume") || q.contains("full volume") || q.contains("maximum volume") || q.contains("முழு சத்தம்")) {
            val speech = if (isTamil) "முழு சத்தம் வைக்கப்படுகிறது" else "Setting volume to maximum"
            return ActionResult.Handled(speech) {
                setVolumeMax()
            }
        }

        // ========================================== 
        // 5. Timers
        // ========================================== 
        if (q.contains("timer") || q.contains("டைமர்")) {
            val timerResult = parseAndSetTimer(q)
            if (timerResult != null) return timerResult
        }

        // ========================================== 
        // 6. Alarms with exact time parsing
        // ========================================== 
        if (q.contains("alarm") || q.contains("அலாரம்")) {
            val alarmResult = parseAndSetAlarm(q)
            if (alarmResult != null) return alarmResult
        }

        // ========================================== 
        // 7. Phone Call / Dial
        // ========================================== 
        if (q.startsWith("call ") || q.startsWith("dial ") || q.contains("கால் பண்ணு")) {
            val numberOrName = q.removePrefix("call ").removePrefix("dial ").replace("கால் பண்ணு", "").trim()
            val digits = numberOrName.filter { it.isDigit() || it == '+' }
            if (digits.length >= 3) {
                val speech = if (isTamil) "$digits எண்ணுக்கு அழைக்கப்படுகிறது" else "Calling $digits"
                return ActionResult.Handled(speech) {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }
        }

        // ========================================== 
        // 8. YouTube specific commands
        // ========================================== 
        if (q.contains("on youtube") || q.contains("யூடியூபில்") || q.contains("youtubeil")) {
            val searchQuery = q.replace("on youtube", "")
                .replace("யூடியூபில்", "")
                .replace("youtubeil", "")
                .replace("search", "")
                .replace("play", "")
                .replace("find", "")
                .replace("தேடு", "")
                .replace("போடு", "")
                .trim()
            if (searchQuery.isNotBlank()) {
                return handleSearchYouTube(searchQuery, isTamil)
            } else {
                return handleOpenApp("youtube", isTamil)
            }
        }

        // ========================================== 
        // 9. Google Web search
        // ========================================== 
        if (q.contains("on google") || q.startsWith("google ") || q.contains("கூகிளில்")) {
            val searchQuery = q.replace("on google", "")
                .replace("கூகிளில்", "")
                .removePrefix("google ")
                .removePrefix("search ")
                .replace("தேடு", "")
                .trim()
            if (searchQuery.isNotBlank()) {
                return handleWebSearch(searchQuery, isTamil)
            }
        }

        // ========================================== 
        // 10. Open App commands ("open youtube", "whatsapp திற", etc.)
        // ========================================== 
        if (q.startsWith("open ") || q.startsWith("launch ") || q.startsWith("start ") || q.startsWith("go to ") || 
            q.endsWith(" திற") || q.endsWith(" open pannu") || q.endsWith(" thira")) {
            val target = q.removePrefix("open ")
                .removePrefix("launch ")
                .removePrefix("start ")
                .removePrefix("go to ")
                .removePrefix("the ")
                .removeSuffix(" திற")
                .removeSuffix(" open pannu")
                .removeSuffix(" thira")
                .trim()
            val result = handleOpenApp(target, isTamil)
            if (result is ActionResult.Handled) return result
        }

        // Direct app name matches
        if (q in listOf("youtube", "whatsapp", "camera", "settings", "chrome", "spotify", "instagram", "calculator", "யூடியூப்", "வாட்ஸ்அப்", "கேமரா")) {
            val result = handleOpenApp(q, isTamil)
            if (result is ActionResult.Handled) return result
        }

        // ========================================== 
        // 11. Camera / Photo
        // ========================================== 
        if (q.contains("camera") || q.contains("take picture") || q.contains("take photo") || 
            q.contains("கேமரா") || q.contains("போட்டோ எடு") || q.contains("புகைப்படம்")) {
            val speech = if (isTamil) "கேமரா திறக்கப்படுகிறது" else "Opening Camera"
            return ActionResult.Handled(speech) {
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }

        return ActionResult.NotHandled
    }

    // --- Flashlight Helper ---
    private fun setFlashlight(enabled: Boolean) {
        try {
            val camManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraId = camManager?.cameraIdList?.firstOrNull()
            if (cameraId != null) {
                camManager.setTorchMode(cameraId, enabled)
            }
        } catch (_: Throwable) {}
    }

    // --- Volume Helpers ---
    private fun setVolumeMute() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
        } catch (_: Throwable) {}
    }

    private fun setVolumeMax() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val max = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, max, AudioManager.FLAG_SHOW_UI)
        } catch (_: Throwable) {}
    }

    // --- Smart Alarm Parser ---
    private fun parseAndSetAlarm(query: String): ActionResult? {
        val isTamil = query.any { it in '\u0B80'..'\u0BFF' } || query.contains("alaraam")
        var hour = -1
        var minutes = 0
        val isPm = query.contains("pm") || query.contains("night") || query.contains("evening") || query.contains("இரவு") || query.contains("மாலை")
        val isAm = query.contains("am") || query.contains("morning") || query.contains("காலை")

        // Try parsing "7:30" pattern
        val timeColonRegex = Regex("""(\d{1,2}):(\d{2})""")
        val colonMatch = timeColonRegex.find(query)
        if (colonMatch != null) {
            hour = colonMatch.groupValues[1].toIntOrNull() ?: -1
            minutes = colonMatch.groupValues[2].toIntOrNull() ?: 0
        } else {
            // Match word numbers or digits: "7 o clock", "7 clock", "at 7", "for 7"
            val digitRegex = Regex("""\b(\d{1,2})\b""")
            val matches = digitRegex.findAll(query).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()
            for (n in matches) {
                if (n in 1..24) {
                    hour = n
                    break
                }
            }
        }

        if (hour != -1) {
            // Adjust for 12h AM/PM
            if (isPm && hour < 12) hour += 12
            if (isAm && hour == 12) hour = 0
            // Default afternoon hours (e.g. 1 to 5) to PM if not specified
            if (!isAm && !isPm && hour in 1..5) hour += 12

            val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val amPmStr = if (hour >= 12) "PM" else "AM"
            val minuteStr = if (minutes < 10) "0$minutes" else "$minutes"
            val speech = if (isTamil) {
                "அலாரம் $displayHour:$minuteStr $amPmStr-க்கு வைக்கப்படுகிறது"
            } else {
                "Setting alarm for $displayHour:$minuteStr $amPmStr"
            }

            return ActionResult.Handled(speech) {
                try {
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, minutes)
                        putExtra(AlarmClock.EXTRA_MESSAGE, "Siri Alarm")
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    val fallback = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallback)
                }
            }
        }

        // Generic alarm fallback
        val fallbackSpeech = if (isTamil) "அலாரம் திறக்கப்படுகிறது" else "Opening Alarm clock"
        return ActionResult.Handled(fallbackSpeech) {
            try {
                val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    // --- Smart Timer Parser ---
    private fun parseAndSetTimer(query: String): ActionResult? {
        val isTamil = query.any { it in '\u0B80'..'\u0BFF' } || query.contains("timer")
        val regex = Regex("""\b(\d+)\s*(minute|minutes|min|mins|second|seconds|sec|secs|நிமிடம்|நொடி)\b""")
        val match = regex.find(query)
        if (match != null) {
            val value = match.groupValues[1].toIntOrNull() ?: 1
            val unit = match.groupValues[2]
            val totalSeconds = if (unit.startsWith("sec") || unit == "நொடி") value else value * 60
            val unitLabel = if (unit.startsWith("sec") || unit == "நொடி") {
                if (isTamil) "$value நொடிகள்" else "$value seconds"
            } else {
                if (isTamil) "$value நிமிடங்கள்" else "$value minutes"
            }

            val speech = if (isTamil) "டைமர் $unitLabel வைக்கப்படுகிறது" else "Setting timer for $unitLabel"
            return ActionResult.Handled(speech) {
                try {
                    val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, totalSeconds)
                        putExtra(AlarmClock.EXTRA_MESSAGE, "Timer")
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    val fallback = Intent(AlarmClock.ACTION_SHOW_TIMERS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallback)
                }
            }
        }
        return null
    }

    private fun handleOpenApp(appName: String, isTamil: Boolean = false): ActionResult {
        val pm = context.packageManager
        val clean = appName.replace("app", "")
            .replace("ஆப்", "")
            .replace("திற", "")
            .replace("open", "")
            .trim()
            .lowercase()
        if (clean.isBlank()) return ActionResult.NotHandled

        val knownPackages = mapOf(
            "youtube" to "com.google.android.youtube",
            "யூடியூப்" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "வாட்ஸ்அப்" to "com.whatsapp",
            "chrome" to "com.android.chrome",
            "குரோம்" to "com.android.chrome",
            "browser" to "com.android.chrome",
            "உலாவி" to "com.android.chrome",
            "instagram" to "com.instagram.android",
            "இன்ஸ்டாகிராம்" to "com.instagram.android",
            "spotify" to "com.spotify.music",
            "ஸ்பாட்டிஃபை" to "com.spotify.music",
            "telegram" to "org.telegram.messenger",
            "டெலிகிராம்" to "org.telegram.messenger",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "மேப்ஸ்" to "com.google.android.apps.maps",
            "gmail" to "com.google.android.gm",
            "மின்னஞ்சல்" to "com.google.android.gm",
            "play store" to "com.android.vending",
            "ப்ளே ஸ்டோர்" to "com.android.vending"
        )

        if (clean == "settings" || clean == "phone settings" || clean == "செட்டிங்ஸ்") {
            val speech = if (isTamil) "செட்டிங்ஸ் திறக்கப்படுகிறது" else "Opening Settings"
            return ActionResult.Handled(speech) {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }

        val pkg = knownPackages[clean]
        if (pkg != null) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                val display = clean.replaceFirstChar { it.uppercase() }
                val speech = if (isTamil) "$display திறக்கப்படுகிறது" else "Opening $display"
                return ActionResult.Handled(speech) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                }
            }
        }

        // Dynamic search across all installed apps by label
        try {
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (info in installed) {
                val label = pm.getApplicationLabel(info).toString().lowercase()
                if (label == clean || label.contains(clean) || clean.contains(label)) {
                    val launchIntent = pm.getLaunchIntentForPackage(info.packageName)
                    if (launchIntent != null) {
                        val displayLabel = pm.getApplicationLabel(info).toString()
                        val speech = if (isTamil) "$displayLabel திறக்கப்படுகிறது" else "Opening $displayLabel"
                        return ActionResult.Handled(speech) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // If not installed on the phone, inform the user and open Play Store
        val notInstalledSpeech = if (isTamil) {
            "$clean உங்கள் போனில் இல்லை. Play Store-ல் திறக்கப்படுகிறது"
        } else {
            "$clean is not installed on your phone. Opening Play Store..."
        }
        return ActionResult.Handled(notInstalledSpeech) {
            try {
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(clean))).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(marketIntent)
            } catch (_: Exception) {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=" + Uri.encode(clean) + "&c=apps")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            }
        }
    }

    private fun handleSearchYouTube(query: String, isTamil: Boolean = false): ActionResult {
        val speech = if (isTamil) "யூடியூபில் $query தேடப்படுகிறது" else "Searching YouTube for $query"
        return ActionResult.Handled(speech) {
            try {
                val intent = Intent(Intent.ACTION_SEARCH).apply {
                    setPackage("com.google.android.youtube")
                    putExtra("query", query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            }
        }
    }

    private fun handleWebSearch(query: String, isTamil: Boolean = false): ActionResult {
        val speech = if (isTamil) "கூகிளில் $query தேடப்படுகிறது" else "Searching Google for $query"
        return ActionResult.Handled(speech) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    private fun sendMediaKeyEvent(keyCode: Int) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            audioManager?.dispatchMediaKeyEvent(eventDown)
            audioManager?.dispatchMediaKeyEvent(eventUp)
        } catch (_: Throwable) {}
    }

    private fun goToHomeScreen() {
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(homeIntent)
        } catch (_: Throwable) {}
    }
}