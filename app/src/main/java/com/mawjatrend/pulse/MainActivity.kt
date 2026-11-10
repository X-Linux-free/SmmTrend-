package com.mawjatrend.pulse

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** One screen, built in code (no XML layouts). Arabic UI, dark theme matching the panel. */
class MainActivity : Activity() {
    private val orange = Color.parseColor("#FF8A00")
    private lateinit var url: EditText
    private lateinit var secret: EditText
    private lateinit var senders: EditText
    private lateinit var keywords: EditText
    private lateinit var enabled: Switch
    private lateinit var status: TextView
    private lateinit var logView: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun label(t: String) = TextView(this).apply { text = t; setTextColor(Color.parseColor("#B9B2A8")); textSize = 13f; setPadding(0, dp(14), 0, dp(4)) }
    private fun field(hint: String, pwd: Boolean = false) = EditText(this).apply {
        this.hint = hint; setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#6B655C")); textSize = 15f
        setSingleLine(true); setPadding(dp(14), dp(12), dp(14), dp(12)); layoutDirection = View.LAYOUT_DIRECTION_LTR; gravity = Gravity.START
        inputType = if (pwd) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        background = GradientDrawable().apply { setColor(Color.parseColor("#1B1914")); cornerRadius = dp(12).toFloat(); setStroke(dp(1), Color.parseColor("#3A3126")) }
    }
    private fun btn(t: String, primary: Boolean, onClick: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; setTextColor(if (primary) Color.parseColor("#1A1000") else Color.WHITE); textSize = 15f; setTypeface(null, Typeface.BOLD)
        background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); if (primary) setColor(orange) else { setColor(Color.TRANSPARENT); setStroke(dp(1), orange) } }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(10) }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = Color.parseColor("#0C0B0A"); window.navigationBarColor = Color.parseColor("#0C0B0A")
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(28), dp(20), dp(28)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        root.addView(TextView(this).apply { text = "نبض موجة"; setTextColor(orange); textSize = 30f; setTypeface(null, Typeface.BOLD) })
        root.addView(TextView(this).apply { text = "يلتقط رسائل استلام الأموال على هذا الهاتف ويرسلها بأمان إلى موقعك ليتحقق من التحويلات تلقائياً."; setTextColor(Color.parseColor("#B9B2A8")); textSize = 14f; setPadding(0, dp(4), 0, dp(8)) })
        status = TextView(this).apply { textSize = 14f; setPadding(dp(14), dp(12), dp(14), dp(12)); background = GradientDrawable().apply { setColor(Color.parseColor("#1B1914")); cornerRadius = dp(12).toFloat() } }
        root.addView(status)
        root.addView(label("رابط الهوك (https)")); url = field("https://yoursite.com/api/payment-hook.php"); root.addView(url)
        root.addView(label("السر (من صفحة Auto-pay في الإدارة)")); secret = field("sms_hook_secret", true); root.addView(secret)
        root.addView(label("المرسلون المسموحون (اختياري، افصل بفاصلة)")); senders = field("VF-Cash, Orange, WE"); senders.inputType = InputType.TYPE_CLASS_TEXT; root.addView(senders)
        root.addView(label("كلمات يجب أن تحتويها الرسالة (حماية للخصوصية)")); keywords = field(""); keywords.inputType = InputType.TYPE_CLASS_TEXT; root.addView(keywords)
        enabled = Switch(this).apply { text = "تشغيل الإرسال"; setTextColor(Color.WHITE); textSize = 15f; setPadding(0, dp(14), 0, dp(4)) }; root.addView(enabled)
        root.addView(btn("حفظ", true) { save(); Toast.makeText(this, "تم الحفظ", Toast.LENGTH_SHORT).show(); refresh() })
        root.addView(btn("السماح بقراءة الرسائل الواردة", false) { requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS), 1) })
        root.addView(btn("إرسال رسالة اختبار", false) { save(); SmsReceiver.enqueue(this, "MAWJA PULSE TEST " + System.currentTimeMillis()); Toast.makeText(this, "أُضيفت لقائمة الإرسال", Toast.LENGTH_SHORT).show() })
        root.addView(btn("إعدادات التطبيق (السماح بالإعدادات المقيّدة)", false) { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) })
        root.addView(btn("استثناء من توفير البطارية", false) { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) })
        root.addView(label("آخر العمليات")); logView = TextView(this).apply { setTextColor(Color.parseColor("#D9D2C7")); textSize = 12f; typeface = Typeface.MONOSPACE; layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(dp(14), dp(12), dp(14), dp(12)); background = GradientDrawable().apply { setColor(Color.parseColor("#14120F")); cornerRadius = dp(12).toFloat() } }
        root.addView(logView)
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0C0B0A")); isFillViewport = true; addView(root) })
        url.setText(Prefs.url(this)); secret.setText(Prefs.secret(this)); senders.setText(Prefs.senders(this)); keywords.setText(Prefs.keywords(this)); enabled.isChecked = Prefs.enabled(this)
    }
    private fun save() = Prefs.save(this, url.text.toString(), secret.text.toString(), senders.text.toString(), keywords.text.toString(), enabled.isChecked)
    override fun onResume() { super.onResume(); refresh() }
    override fun onRequestPermissionsResult(r: Int, p: Array<out String>, g: IntArray) { super.onRequestPermissionsResult(r, p, g); refresh() }
    private fun refresh() {
        val perm = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val cfg = Prefs.url(this).startsWith("https://") && Prefs.secret(this).isNotBlank()
        val ok = perm && cfg && Prefs.enabled(this)
        status.setTextColor(if (ok) Color.parseColor("#5FD18A") else Color.parseColor("#FFB347"))
        status.text = (if (ok) "● يعمل: الرسائل المطابقة تُرسل لموقعك" else "● غير جاهز") +
            "\nإذن الرسائل: " + (if (perm) "مفعّل" else "غير مفعّل") + "   |   الإعدادات: " + (if (cfg) "مكتملة" else "ناقصة")
        logView.text = Prefs.logText(this).ifBlank { "لا توجد عمليات بعد." }
    }
}
