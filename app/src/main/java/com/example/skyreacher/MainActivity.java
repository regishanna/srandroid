package com.example.skyreacher;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private NotificationUtils m_notificationUtils;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.i("SkyReacherXXXXXXXX", "MainActivity.onCreate");

        // creation du canal de notification
        m_notificationUtils = new NotificationUtils(this);

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
    }
}