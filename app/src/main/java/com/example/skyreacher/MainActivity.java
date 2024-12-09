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

    private Handler m_handler;

    Runnable m_updateActivity = new Runnable() {
        @Override
        public void run() {
            // we retrieve the activity from the service
            TrafficService.Status status = TrafficService.getStatus();
            double activity = TrafficService.getActivity();
            Log.i("SkyReacher", "status=" + status + " activity=" + activity);
            if (status != TrafficService.Status.OPERATIONAL)
                activity = 0.0F;

            ProgressBar progressBar = findViewById((R.id.progressBar));

            // activity display
            int maxActivity = 10;
            int maxProgress = progressBar.getMax();
            int displayedActivity = (int) (activity * maxProgress / maxActivity);
            if (displayedActivity > maxProgress)
                displayedActivity = maxProgress;
            progressBar.setProgress(displayedActivity);

            // connectivity display
            int alpha = 0x40;
            int connectivityColor;
            if (status == TrafficService.Status.OPERATIONAL)
                connectivityColor = argb(alpha, 0x0, 0xff, 0x0);    // vert
            else if (status == TrafficService.Status.WAITING_FOR_SELF_LOCATION)
                connectivityColor = argb(alpha, 0xff, 0x80, 0x0);   // orange
            else    // TrafficService.Status.DISCONNECTED
                connectivityColor = argb(alpha, 0xff, 0x0, 0x0);    // rouge
            progressBar.setBackgroundColor(connectivityColor);

            // we restart the activity update
            m_handler.postDelayed(m_updateActivity, 1000 /*ms*/);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.i("SkyReacher", "MainActivity.onCreate");

        // creation of attributes
        m_handler = new Handler();

        // creation of notification channels
        new NotificationUtils(this);

        // checking and requesting permissions
        String[] requestedPermissions = {Manifest.permission.ACCESS_COARSE_LOCATION,
                                         Manifest.permission.ACCESS_BACKGROUND_LOCATION};
        for (String permission : requestedPermissions)
        {
            if (ContextCompat.checkSelfPermission(
                    this, permission) !=
                    PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[] { permission },
                        0);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.i("SkyReacher", "MainActivity.onResume");

        ToggleButton toggle = findViewById(R.id.toggleButton);

        // sets the button state to the service state
        toggle.setChecked(TrafficService.isRunning());

        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Intent intent = new Intent(MainActivity.this, TrafficService.class);
            if (isChecked) {
                startForegroundService(intent);
            } else {
                stopService(intent);
            }
        });

        // we update the activity bar
        m_handler.post(m_updateActivity);
    }

    @Override
    protected void onPause() {
        super.onPause();

        Log.i("SkyReacher", "MainActivity.onPause");

        // we stop updating the activity bar
        m_handler.removeCallbacks(m_updateActivity);
    }

}