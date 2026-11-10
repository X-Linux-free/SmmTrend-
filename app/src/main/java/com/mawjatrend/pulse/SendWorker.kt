package com.mawjatrend.pulse

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class SendWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val c = applicationContext
        val text = inputData.getString("text") ?: return Result.failure()
        val url = Prefs.url(c); val secret = Prefs.secret(c)
        if (!url.startsWith("https://") || secret.isBlank()) { Prefs.log(c, "✗ الإعدادات ناقصة (الرابط يجب أن يبدأ بـ https والسر مطلوب)"); return Result.failure() }
        return try {
            val body = "secret=" + URLEncoder.encode(secret, "UTF-8") + "&text=" + URLEncoder.encode(text, "UTF-8")
            val sp = c.getSharedPreferences("hostguard", Context.MODE_PRIVATE)
            var cookie = sp.getString("test_cookie", "") ?: ""
            var target = if (cookie.isNotEmpty()) withI(url) else url
            var (code, resp) = post(target, body, cookie)
            // InfinityFree browser-check page: solve it (AES), keep the cookie, resend once.
            if (code in 200..299 && resp.contains("aes.js")) {
                val solved = solveChallenge(resp)
                if (solved != null) {
                    cookie = solved; sp.edit().putString("test_cookie", cookie).apply()
                    target = withI(url)
                    val r2 = post(target, body, cookie); code = r2.first; resp = r2.second
                }
            }
            val shortResp = resp.take(300)
            when {
                code in 200..299 && Regex("\"ok\"\\s*:\\s*true").containsMatchIn(shortResp) -> {
                    val matched = if (Regex("\"matched\"\\s*:\\s*null").containsMatchIn(shortResp)) "لم تُطابق طلباً" else "تمت مطابقتها ✓"
                    Prefs.log(c, "✓ أُرسلت — $matched"); Result.success()
                }
                code in 200..299 -> { Prefs.log(c, "⚠ رد غير متوقع (قد تكون حماية الاستضافة): ${shortResp.take(80)}"); Result.failure() }
                code == 403 -> { Prefs.log(c, "✗ السر غير صحيح (403)"); Result.failure() }
                code in 400..499 && code != 429 -> { Prefs.log(c, "✗ رفض الموقع الطلب (HTTP $code)"); Result.failure() }
                else -> retryOrFail("HTTP $code")
            }
        } catch (e: Exception) { retryOrFail(e.javaClass.simpleName) }
    }
    private fun withI(u: String) = if (u.contains("i=1")) u else u + (if (u.contains("?")) "&" else "?") + "i=1"
    private fun post(url: String, body: String, cookie: String): Pair<Int, String> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 15000; readTimeout = 20000; doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) MawjaPulse/1.1")
            if (cookie.isNotEmpty()) setRequestProperty("Cookie", "__test=$cookie")
        }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        return code to resp
    }
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private fun solveChallenge(html: String): String? = try {
        val m = Regex("toNumbers\\(\"([0-9a-fA-F]+)\"\\)").findAll(html).map { it.groupValues[1] }.toList()
        if (m.size < 3) null else {
            val ci = Cipher.getInstance("AES/CBC/NoPadding")
            ci.init(Cipher.DECRYPT_MODE, SecretKeySpec(hex(m[0]), "AES"), IvParameterSpec(hex(m[1])))
            ci.doFinal(hex(m[2])).joinToString("") { "%02x".format(it) }
        }
    } catch (e: Exception) { null }
    private fun retryOrFail(why: String): Result {
        return if (runAttemptCount < 8) { Prefs.log(applicationContext, "↻ تعذر الإرسال ($why) — سأعيد المحاولة"); Result.retry() }
        else { Prefs.log(applicationContext, "✗ فشل نهائي ($why)"); Result.failure() }
    }
}
