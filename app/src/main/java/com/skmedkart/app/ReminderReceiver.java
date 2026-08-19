package com.skmedkart.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "skmedkart_reminders";

    @Override
    public void onReceive(Context context, Intent intent) {

        String customer =
                intent.getStringExtra("customer");

        String message =
                intent.getStringExtra("message");

        if (customer == null || customer.trim().isEmpty()) {
            customer = "Customer";
        }

        if (message == null || message.trim().isEmpty()) {
            message = "Medicine refill reminder";
        }

        NotificationManager nm =
                (NotificationManager)
                        context.getSystemService(
                                Context.NOTIFICATION_SERVICE
                        );

        if (Build.VERSION.SDK_INT >= 26) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "SKMedKART Reminders",
                            NotificationManager.IMPORTANCE_DEFAULT
                    );

            nm.createNotificationChannel(channel);
        }

        Intent open =
                new Intent(
                        context,
                        MainActivity.class
                );

        PendingIntent pi =
                PendingIntent.getActivity(
                        context,
                        1001,
                        open,
                        PendingIntent.FLAG_UPDATE_CURRENT |
                                PendingIntent.FLAG_IMMUTABLE
                );

        android.app.Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= 26) {
            builder =
                    new android.app.Notification.Builder(
                            context,
                            CHANNEL_ID
                    );
        } else {
            builder =
                    new android.app.Notification.Builder(
                            context
                    );
        }

        builder.setSmallIcon(
                        android.R.drawable.ic_dialog_info
                )
                .setContentTitle(
                        "SKMedKART Reminder"
                )
                .setContentText(
                        customer + " • " + message
                )
                .setAutoCancel(true)
                .setContentIntent(pi);

        nm.notify(
                (int) (System.currentTimeMillis() % 100000),
                builder.build()
        );
    }
}
