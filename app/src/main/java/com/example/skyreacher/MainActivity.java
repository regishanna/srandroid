package com.example.skyreacher;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import static android.graphics.Color.argb;

public class MainActivity extends AppCompatActivity {

    private NotificationUtils m_notificationUtils;
    private Handler m_handler;

    Runnable m_updateActivity = new Runnable() {
        @Override
        public void run() {
            // on recupere l'activite depuis le service
            TrafficService.Status status = TrafficService.getStatus();
            double activity = TrafficService.getActivity();
            Log.i("SkyReacherXXXXXXXX", "status=" + status + " activity=" + activity);
            if (status != TrafficService.Status.OPERATIONAL)
                activity = 0.0F;

            ProgressBar progressBar = findViewById((R.id.progressBar));

            // affichage de l'activite
            int maxActivity = 10;
            int maxProgress = progressBar.getMax();
            int displayedActivity = (int) (activity * maxProgress / maxActivity);
            if (displayedActivity > maxProgress)
                displayedActivity = maxProgress;
            progressBar.setProgress(displayedActivity);

            // affichage de la connectivite
            int alpha = 0x40;
            int connectivityColor;
            if (status == TrafficService.Status.OPERATIONAL)
                connectivityColor = argb(alpha, 0x0, 0xff, 0x0);    // vert
            else if (status == TrafficService.Status.WAITING_FOR_SELF_LOCATION)
                connectivityColor = argb(alpha, 0xff, 0x80, 0x0);   // orange
            else    // TrafficService.Status.DISCONNECTED
                connectivityColor = argb(alpha, 0xff, 0x0, 0x0);    // rouge
            progressBar.setBackgroundColor(connectivityColor);

            // on relance la mise a jour de l'activite
            m_handler.postDelayed(m_updateActivity, 1000 /*ms*/);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.i("SkyReacherXXXXXXXX", "MainActivity.onCreate");

        // creation des attributs
        m_notificationUtils = new NotificationUtils(this);
        m_handler = new Handler();

        // verification et demande des permissions
        String[] permissionsDemandees = {Manifest.permission.ACCESS_COARSE_LOCATION,
                                         Manifest.permission.ACCESS_BACKGROUND_LOCATION};
        for (String permission : permissionsDemandees)
        {
            if (ContextCompat.checkSelfPermission(
                    this, permission) !=
                    PackageManager.PERMISSION_GRANTED) {
                // You can directly ask for the permission.
                ActivityCompat.requestPermissions(this,
                        new String[] { permission },
                        0);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.i("SkyReacherXXXXXXXX", "MainActivity.onResume");

        ToggleButton toggle = findViewById(R.id.toggleButton);

        // place l'etat du bouton dans l'etat du service
        toggle.setChecked(TrafficService.isRunning());

        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Intent intent = new Intent(MainActivity.this, TrafficService.class);
            if (isChecked) {
                startForegroundService(intent);
            } else {
                stopService(intent);
            }
        });

        // on met a jour la barre d'activite
        m_handler.post(m_updateActivity);
    }

    @Override
    protected void onPause() {
        super.onPause();

        Log.i("SkyReacherXXXXXXXX", "MainActivity.onPause");

        // on arrete la mise a jour de la barre d'activite
        m_handler.removeCallbacks(m_updateActivity);
    }

}