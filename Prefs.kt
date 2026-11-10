package com.mawjatrend.pulse

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Settings + a small on-device log (last 40 lines). Nothing here leaves the phone except what SendWorker posts to YOUR site. */
object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("pulse", Context.MODE_PRIVATE)
    fun url(c: Context) = sp(c).getString("url", "") ?: ""
    fun secret(c: Context) = sp(c).getString("secret", "") ?: ""
    fun senders(c: Context) = sp(c).getString("senders", "") ?: ""
    fun keywords(c: Context) = sp(c).getString("keywords", "جنيه,EGP,تحويل,استلام,received") ?: ""
    fun enabled(c: Context) = sp(c).getBoolean("enabled", true)
    fun save(c: Context, url: String, secret: String, senders: String, keywords: String, enabled: Boolean) {
        sp(c).edit().putString("url", url.trim()).putString("secret", secret.trim()).putString("senders", senders.trim())
            .putString("keywords", keywords.trim()).putBoolean("enabled", enabled).apply()
    }
    private fun list(s: String) = s.split(',', '،').map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    /** Only forward a message when the sender (if a filter is set) AND a payment keyword match, so OTP codes and chats are never sent. */
    fun shouldForward(c: Context, sender: String, body: String): Boolean {
        val s = list(senders(c)); val k = list(keywords(c))
        if (s.isNotEmpty() && s.none { sender.lowercase().contains(it) }) return false
        if (k.isNotEmpty() && k.none { body.lowercase().contains(it) }) return false
        return true
    }
    @Synchronized fun log(c: Context, line: String) {
        val t = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())
        val old = (sp(c).getString("log", "") ?: "").split("\n").filter { it.isNotBlank() }
        sp(c).edit().putString("log", (listOf("$t  $line") + old).take(40).joinToString("\n")).apply()
    }
    fun logText(c: Context) = sp(c).getString("log", "") ?: ""
}
