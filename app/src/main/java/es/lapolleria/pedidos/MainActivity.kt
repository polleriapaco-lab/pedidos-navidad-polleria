package es.lapolleria.pedidos

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.time.LocalDate
import java.time.ZoneId

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        fileCallback?.onReceiveValue(uri?.let { arrayOf(it) })
        fileCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        requestNotificationPermission()

        webView = WebView(this)
        setContentView(webView)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            builtInZoomControls = false
            displayZoomControls = false
        }
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                filePicker.launch("application/json")
                return true
            }
        }
        webView.addJavascriptInterface(AppBridge(), "Android")
        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    "pedidos",
                    getString(R.string.notification_channel),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Avisos de pedidos que se recogen al día siguiente" }
            )
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 20)
    }

    inner class AppBridge {
        @JavascriptInterface
        fun openWhatsApp(phone: String, message: String) {
            runOnUiThread {
                val clean = phone.filter(Char::isDigit).let { if (it.length == 9) "34$it" else it }
                val uri = Uri.parse("https://wa.me/$clean?text=${Uri.encode(message)}")
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
        }

        @JavascriptInterface
        fun shareBackup(json: String) {
            runOnUiThread {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_TEXT, json)
                    putExtra(Intent.EXTRA_TITLE, "Copia de pedidos de Navidad")
                }, "Guardar o compartir copia"))
            }
        }

        @JavascriptInterface
        fun printPage() {
            runOnUiThread {
                val manager = getSystemService(Context.PRINT_SERVICE) as PrintManager
                manager.print(
                    "Pedidos La Pollería 2.0",
                    webView.createPrintDocumentAdapter("Pedidos La Pollería 2.0"),
                    PrintAttributes.Builder().build()
                )
            }
        }

        @JavascriptInterface
        fun scheduleReminder(orderNumber: String, customer: String, pickupDate: String) {
            val date = runCatching { LocalDate.parse(pickupDate).minusDays(1) }.getOrNull() ?: return
            val trigger = date.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (trigger <= System.currentTimeMillis()) return
            val intent = Intent(this@MainActivity, ReminderReceiver::class.java).apply {
                putExtra("number", orderNumber)
                putExtra("customer", customer)
                putExtra("date", pickupDate)
            }
            val pending = PendingIntent.getBroadcast(
                this@MainActivity,
                orderNumber.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            (getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        }

        @JavascriptInterface
        fun cancelReminder(orderNumber: String) {
            val pending = PendingIntent.getBroadcast(
                this@MainActivity,
                orderNumber.hashCode(),
                Intent(this@MainActivity, ReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) ?: return
            (getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
        }
    }
}
