package com.mawjatrend.pulse

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SendWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val c = applicationContext
        val text = inputData.getString("text") ?: return Result.failure()
        val url = Prefs.url(c); val secret = Prefs.secret(c)
        if (!url.startsWith("https://") || secret.isBlank()) { Prefs.log(c, "✗ الإعدادات ناقصة (الرابط يجب أن يبدأ بـ https والسر مطلوب)"); return Result.failure() }
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 15000; readTimeout = 20000; doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                setRequestProperty("User-Agent", "MawjaPulse/1.0")
            }
            val body = "secret=" + URLEncoder.encode(secret, "UTF-8") + "&text=" + URLEncoder.encode(text, "UTF-8")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }?.take(300) ?: ""
            conn.disconnect()
            when {
                code in 200..299 && Regex("\"ok\"\\s*:\\s*true").containsMatchIn(resp) -> {
                    val matched = if (Regex("\"matched\"\\s*:\\s*null").containsMatchIn(resp)) "لم تُطابق طلباً" else "تمت مطابقتها ✓"
                    Prefs.log(c, "✓ أُرسلت — $matched"); Result.success()
                }
                code in 200..299 -> { Prefs.log(c, "⚠ رد غير متوقع (قد تكون حماية الاستضافة): ${resp.take(80)}"); Result.failure() }
                code == 403 -> { Prefs.log(c, "✗ السر غير صحيح (403)"); Result.failure() }
                code in 400..499 && code != 429 -> { Prefs.log(c, "✗ رفض الموقع الطلب (HTTP $code)"); Result.failure() }
                else -> retryOrFail("HTTP $code")
            }
        } catch (e: Exception) { retryOrFail(e.javaClass.simpleName) }
    }
    private fun retryOrFail(why: String): Result {
        return if (runAttemptCount < 8) { Prefs.log(applicationContext, "↻ تعذر الإرسال ($why) — سأعيد المحاولة"); Result.retry() }
        else { Prefs.log(applicationContext, "✗ فشل نهائي ($why)"); Result.failure() }
    }
}
