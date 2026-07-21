package es.lapolleria.pedidos

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val number = intent.getStringExtra("number") ?: ""
        val customer = intent.getStringExtra("customer") ?: "Cliente"
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, "pedidos")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Pedido $number para mañana")
            .setContentText("$customer recoge mañana. Abra la aplicación para ver el pedido y enviar WhatsApp.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$customer recoge mañana el pedido $number. Abra la aplicación para revisar todos los datos y enviar el recordatorio por WhatsApp."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(number.hashCode(), notification)
    }
}
