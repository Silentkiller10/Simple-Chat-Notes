package com.example.media

import java.net.URI
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

object MediaUtils {

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, index.toDouble())
        return if (index == 0) "$bytes B" else String.format(Locale.US, "%.1f %s", value, units[index])
    }

    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0) return "0:00"
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes >= 60) {
            val hours = minutes / 60
            val remMinutes = minutes % 60
            String.format(Locale.US, "%d:%02d:%02d", hours, remMinutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    fun formatTime(timeMillis: Long): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date(timeMillis))
    }

    fun formatDateHeader(timeMillis: Long): String {
        val messageCal = Calendar.getInstance().apply { timeInMillis = timeMillis }
        val nowCal = Calendar.getInstance()

        return when {
            isSameDay(messageCal, nowCal) -> "TODAY"
            isYesterday(messageCal, nowCal) -> "YESTERDAY"
            messageCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) -> {
                SimpleDateFormat("MMMM d", Locale.getDefault()).format(Date(timeMillis)).uppercase(Locale.getDefault())
            }
            else -> {
                SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(timeMillis)).uppercase(Locale.getDefault())
            }
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(cal1: Calendar, cal2: Calendar): Boolean {
        val cal2Copy = cal2.clone() as Calendar
        cal2Copy.add(Calendar.DAY_OF_YEAR, -1)
        return isSameDay(cal1, cal2Copy)
    }

    private val URL_PATTERN = Pattern.compile(
        "(https?://[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]+)",
        Pattern.CASE_INSENSITIVE
    )

    fun extractUrls(text: String): List<String> {
        val urls = mutableListOf<String>()
        val matcher = URL_PATTERN.matcher(text)
        while (matcher.find()) {
            urls.add(matcher.group())
        }
        return urls
    }

    fun extractDomain(urlString: String): String {
        return try {
            val uri = URI(urlString)
            val domain = uri.host ?: urlString
            if (domain.startsWith("www.")) domain.substring(4) else domain
        } catch (_: Exception) {
            urlString
        }
    }

    private val TAG_PATTERN = Pattern.compile("#(\\w+)")

    fun extractTags(text: String): List<String> {
        val tags = mutableListOf<String>()
        val matcher = TAG_PATTERN.matcher(text)
        while (matcher.find()) {
            tags.add("#" + matcher.group(1))
        }
        return tags.distinct()
    }

    fun extractHashtags(text: String): List<String> = extractTags(text)

    fun getFileExtension(filename: String): String {
        val dot = filename.lastIndexOf('.')
        return if (dot >= 0 && dot < filename.length - 1) {
            filename.substring(dot + 1).uppercase(Locale.getDefault())
        } else {
            "FILE"
        }
    }

    fun isImageMime(mime: String): Boolean = mime.startsWith("image/")
    fun isVideoMime(mime: String): Boolean = mime.startsWith("video/")
    fun isAudioMime(mime: String): Boolean = mime.startsWith("audio/")
}
