package com.example.skyreacher;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

public class NotificationUtils {

    public static final String CHANNEL_DEFAULT_IMPORTANCE = "CHANNEL_DEFAULT_IMPORTANCE";
    private static final String CHANNEL_DEFAULT_IMPORTANCE_NAME = "Canal par defaut";

    private final Context m_context;

    public NotificationUtils(Context context) {
        m_context = context;
        createChannels();
    }

    public void createChannels() {
        // create android channel
        NotificationChannel channel = new NotificationChannel(CHANNEL_DEFAULT_IMPORTANCE,
                CHANNEL_DEFAULT_IMPORTANCE_NAME, NotificationManager.IMPORTANCE_LOW);

        getManager().createNotificationChannel(channel);
    }

    private NotificationManager getManager() {
        return (NotificationManager) m_context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

}
