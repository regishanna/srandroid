package com.example.skyreacher;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

public class TrafficService extends Service {

    // traffic service states
    public enum Status {
        DISCONNECTED,
        WAITING_FOR_SELF_LOCATION,
        OPERATIONAL
    }

    private static class TrafficThread extends Thread {

        boolean m_stop;
        private Socket m_socketTcp;
        private Location m_lastLocation;

        // connection status
        private Status m_socketStatus;
        private long m_lastGetActivityNbRx;         // number of receptions since the last activity reading
        private long m_lastGetActivityTimestampNs;  // timestamp in nano seconds of last activity reading

        TrafficThread() {
            m_stop = false;
            m_socketTcp = null;
            m_lastLocation = null;

            m_socketStatus = Status.DISCONNECTED;
            getActivity();  // to reset activity stats
        }

        @Override
        public void run() {
            while (!m_stop) {
                try {
                    m_socketTcp = new Socket("regishanna.hd.free.fr", 1664);
                    m_socketStatus = Status.WAITING_FOR_SELF_LOCATION;
                    m_socketTcp.setSoTimeout(30000 /*ms*/);
                    DatagramSocket socketUdp = new DatagramSocket();
                    DgramOStream dgramOStream = new DgramOStream(500);
                    InetAddress navAppliAddress = InetAddress.getByName("localhost");
                    int navAppliPort = 4000;

                    // if a location already exists, we send it
                    if (m_lastLocation != null)
                        sendLocation(m_lastLocation);

                    while (!m_stop) {
                        final byte[] dgram = dgramOStream.recv(m_socketTcp);
                        if (dgram != null) {
                            Log.i("SkyReacher", dgram.length + "bytes received");
                            m_lastGetActivityNbRx++;
                            DatagramPacket message = new DatagramPacket(dgram, dgram.length, navAppliAddress, navAppliPort);
                            socketUdp.send(message);
                        }
                    }

                    m_socketTcp.close();
                    m_socketStatus = Status.DISCONNECTED;


                } catch (Exception e) {
                    e.printStackTrace();
                    try {
                        m_socketTcp.close();
                    } catch (Exception ex) {/* nothing to do */}
                    m_socketStatus = Status.DISCONNECTED;
                    // in the event of an error, we delay the retry
                    try {
                        Thread.sleep(5000 /*ms*/);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }

        public void sendLocation(Location location) {
            // we memorize the location to be able to send it again during a new connection
            m_lastLocation = location;

            // calculation of the values to send, in millionths of degrees
            int latitude = (int) (location.getLatitude() * 1000000.0);
            int longitude = (int) (location.getLongitude() * 1000000.0);

            // data serialization
            byte[] message = new byte[8];
            int position = 0;
            position = serializeInt(message, position, latitude);
            serializeInt(message, position, longitude);

            // sending the message in a dedicated thread so as not to block the main loop
            new Thread(() -> {
                try {
                    DgramOStream.send(m_socketTcp, message);
                    m_socketStatus = Status.OPERATIONAL;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }

        private int serializeInt(byte[] buffer, int position, int value) {
            buffer[position] = (byte) ((value >> 24) & 0xff);
            buffer[position + 1] = (byte) ((value >> 16) & 0xff);
            buffer[position + 2] = (byte) ((value >> 8) & 0xff);
            buffer[position + 3] = (byte) (value & 0xff);
            return (position + 4);
        }

        // activity in number of positions received per second
        // since the last call to this function
        public double getActivity() {
            // time elapsed since last call
            long currentTimestampNs = System.nanoTime();
            long deltaNs = currentTimestampNs - m_lastGetActivityTimestampNs;

            // calculation of activity
            double activity = ((double) m_lastGetActivityNbRx * 1000000000.0F) / (double) deltaNs;

            // reset stats
            m_lastGetActivityTimestampNs = currentTimestampNs;
            m_lastGetActivityNbRx = 0;

            return activity;
        }

        public Status getSocketStatus() {
            return m_socketStatus;
        }

    }

    private static TrafficThread m_thread = null;
    private static boolean m_isRunning = false;
    private FusedLocationProviderClient m_fusedLocationClient;
    private final LocationCallback m_locationCallback;

    public TrafficService() {
        m_locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    Log.i("SkyReacher", "new location : " +
                            "lat=" + location.getLatitude() + " long=" + location.getLongitude());
                    m_thread.sendLocation(location);
                }
            }
        };
    }

    @Override
    public void onCreate() {

        Log.i("SkyReacher", "TrafficService.onCreate");

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent,
                        PendingIntent.FLAG_IMMUTABLE);

        Notification notification =
                new Notification.Builder(this, NotificationUtils.CHANNEL_DEFAULT_IMPORTANCE)
                        .setContentTitle("Service en tâche de fond")
                        .setContentText("Appuyer pour arrêter l'envoi des traffics proches")
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentIntent(pendingIntent)
                        .build();

        // notification ID cannot be 0.
        startForeground(100, notification);

        // launch of traffic thread
        m_thread = new TrafficThread();
        m_thread.start();

        m_fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // launch defers from the location request to allow time to connect the socket
        Handler handler = new Handler();
        handler.postDelayed(this::launchLocation, 1000 /*ms*/);

        m_isRunning = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // if we get killed, after returning from here, restart
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // no binding offered
        return null;
    }

    @Override
    public void onDestroy() {
        // stop the location request
        try {
            m_fusedLocationClient.removeLocationUpdates(m_locationCallback);
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        // stop the dedicated thread
        m_thread.m_stop = true;

        m_isRunning = false;
    }

    private void launchLocation() {
        final long interval_ms = 60000;
        LocationRequest locationRequest = LocationRequest.create()
                .setInterval(interval_ms)
                .setFastestInterval(interval_ms);
        try {
            m_fusedLocationClient.requestLocationUpdates(locationRequest,
                    m_locationCallback,
                    Looper.getMainLooper());
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    public static boolean isRunning() {
        return m_isRunning;
    }

    public static Status getStatus() {
        Status status = Status.DISCONNECTED;
        if (m_thread != null)
            status = m_thread.getSocketStatus();
        return status;
    }

    public static double getActivity() {
        double activity = 0.0F;
        if (m_thread != null)
            activity = m_thread.getActivity();
        return activity;
    }

}