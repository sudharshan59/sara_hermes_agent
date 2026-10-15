package com.sara.hermes.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings

class DeviceActionHandler(private val context: Context) {

    sealed class ActionResult {
        data class Handled(val speechResponse: String, val action: () -> Unit) : ActionResult()
        object NotHandled : ActionResult()
    }

    fun tryExecute(query: String): ActionResult {
        val raw = query.trim().lowercase()
        // Normalize punctuation
        val q = raw.replace(Regex("[.,!?]"), "").trim()

        // 1. YouTube specific commands
        if (q.contains("on youtube")) {
            val searchQuery = q.replace("on youtube", "")
                .replace("search", "")
                .replace("play", "")
                .replace("find", "")
                .trim()
            if (searchQuery.isNotBlank()) {
                return handleSearchYouTube(searchQuery)
            } else {
                return handleOpenApp("youtube")
            }
        }

        // 2. Open App commands ("open youtube", "launch whatsapp", "open camera")
        if (q.startsWith("open ") || q.startsWith("launch ") || q.startsWith("start ") || q.startsWith("go to ")) {
            val target = q.removePrefix("open ")
                .removePrefix("launch ")
                .removePrefix("start ")
                .removePrefix("go to ")
                .removePrefix("the ")
                .trim()
            val result = handleOpenApp(target)
            if (result is ActionResult.Handled) return result
        }

        // 3. Direct app name matches (user just says "youtube", "whatsapp", "camera")
        if (q in listOf("youtube", "whatsapp", "camera", "settings", "chrome", "spotify", "instagram", "calculator")) {
            val result = handleOpenApp(q)
            if (result is ActionResult.Handled) return result
        }

        // 4. Web search ("search X on google", "google X")
        if (q.contains("on google") || q.startsWith("google ")) {
            val searchQuery = q.replace("on google", "")
                .removePrefix("google ")
                .removePrefix("search ")
                .trim()
            if (searchQuery.isNotBlank()) {
                return handleWebSearch(searchQuery)
            }
        }

        // 5. Alarm / Timer ("set alarm", "set an alarm")
        if (q.contains("alarm")) {
            return ActionResult.Handled("Opening Alarm clock") {
                try {
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    val clockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(clockIntent)
                }
            }
        }

        // 6. Camera / Photo ("take a picture", "take photo", "open camera")
        if (q.contains("camera") || q.contains("take picture") || q.contains("take photo")) {
            return ActionResult.Handled("Opening Camera") {
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }

        return ActionResult.NotHandled
    }

    private fun handleOpenApp(appName: String): ActionResult {
        val pm = context.packageManager
        val clean = appName.replace("app", "").trim()

        // Well-known app package mapping
        val knownPackages = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "chrome" to "com.android.chrome",
            "browser" to "com.android.chrome",
            "instagram" to "com.instagram.android",
            "spotify" to "com.spotify.music",
            "telegram" to "org.telegram.messenger",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "gmail" to "com.google.android.gm",
            "email" to "com.google.android.gm",
            "play store" to "com.android.vending",
            "store" to "com.android.vending"
        )

        // Settings special handling
        if (clean == "settings" || clean == "phone settings") {
            return ActionResult.Handled("Opening Settings") {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }

        // Check known package ID first
        val pkg = knownPackages[clean]
        if (pkg != null) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                val display = clean.replaceFirstChar { it.uppercase() }
                return ActionResult.Handled("Opening $display") {
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
                        return ActionResult.Handled("Opening $displayLabel") {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return ActionResult.NotHandled
    }

    private fun handleSearchYouTube(query: String): ActionResult {
        return ActionResult.Handled("Searching YouTube for $query") {
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

    private fun handleWebSearch(query: String): ActionResult {
        return ActionResult.Handled("Searching Google for $query") {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }
}