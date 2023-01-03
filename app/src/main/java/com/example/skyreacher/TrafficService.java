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

    // etats du service de trafic
    enum Status {
        DISCONNECTED,
        WAITING_FOR_SELF_LOCATION,
        OPERATIONAL
    }

    private static class TrafficThread extends Thread {

        boolean m_stop;
        private Socket m_socketTcp;
        private Location m_lastLocation;

        // etat de la connexion
        private Status m_socketStatus;
        private long m_lastGetActivityNbRx;         // nombre de receptions depuis la derniere lecture d'activite
        private long m_lastGetActivityTimestampNs;  // timestamp en nano secondes de la derniere lecture d'activite

        TrafficThread() {
            m_stop = false;
            m_socketTcp = null;
            m_lastLocation = null;

            m_socketStatus = Status.DISCONNECTED;
            getActivity();  // pour resetter les stats d'activite
        }

        @Override
        public void run() {
            while (!m_stop) {
                try {
                    m_socketTcp = new Socket("82.66.214.245", 1664);
                    m_socketStatus = Status.WAITING_FOR_SELF_LOCATION;
                    m_socketTcp.setSoTimeout(30000 /*ms*/);
                    DatagramSocket socketUdp = new DatagramSocket();
                    DgramOStream dgramOStream = new DgramOStream(500);
                    InetAddress adresseAppliNav = InetAddress.getByName("localhost");
                    int portAppliNav = 4000;

                    // si une localisation existe deja, on l'envoi
                    if (m_lastLocation != null)
                        sendLocation(m_lastLocation);

                    while (!m_stop) {
                        final byte[] dgram = dgramOStream.recv(m_socketTcp);
                        if (dgram != null) {
                            Log.i("Sky Reacher", dgram.length + "octets recus");
                            m_lastGetActivityNbRx++;
                            DatagramPacket message = new DatagramPacket(dgram, dgram.length, adresseAppliNav, portAppliNav);
                            socketUdp.send(message);
                        }
                    }

                    m_socketTcp.close();
                    m_socketStatus = Status.DISCONNECTED;


                } catch (Exception e) {
                    e.printStackTrace();
                    try {
                        m_socketTcp.close();
                    } catch (Exception ex) {/* rien a faire */}
                    m_socketStatus = Status.DISCONNECTED;
                    // en cas d'erreur, on temporise le re-essai
                    try {
                        Thread.sleep(5000 /*ms*/);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }

        public void sendLocation(Location location) {
            // on memorise la localisation pour pouvoir la renvoyer lors d'une nouvelle connexion
            m_lastLocation = location;

            // calcul des valeurs a envoyer, cf la structure position_msg_t dans le code du serveur
            int latitude = (int) (location.getLatitude() * 1000000.0);
            int longitude = (int) (location.getLongitude() * 1000000.0);

            // serialisation des donnees
            byte[] message = new byte[8];
            int position = 0;
            position = serializeInt(message, position, latitude);
            serializeInt(message, position, longitude);

            // envoi du message dans un thread dedie pour ne pas bloquer la main loop
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

        // activite en nombre de positions recues par seconde
        // depuis le dernier appel a cette fonction
        public double getActivity() {
            // temps ecoule depuis le dernier appel
            long currentTimestampNs = System.nanoTime();
            long deltaNs = currentTimestampNs - m_lastGetActivityTimestampNs;

            // calcul de l'activite
            double activity = ((double) m_lastGetActivityNbRx * 1000000000.0F) / (double) deltaNs;

            // reset des stats
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
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    Log.w("SkyReacherXXXXXXXX", "locationResult == null");
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    Log.i("SkyReacherXXXXXXXX", "nouvelle localisation : " +
                            "lat=" + location.getLatitude() + " long=" + location.getLongitude());
                    m_thread.sendLocation(location);
                }
            }
        };
    }

    @Override
    public void onCreate() {

        Log.i("SkyReacherXXXXXXXX", "TrafficService.onCreate");

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent,
                        PendingIntent.FLAG_IMMUTABLE);

        Notification notification =
                new Notification.Builder(this, NotificationUtils.CHANNEL_DEFAULT_IMPORTANCE)
                        .setContentTitle("Service en tache de fond")
                        .setContentText("Appuyer pour arrêter l'envoi des traffics proches")
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentIntent(pendingIntent)
                        .build();

        // Notification ID cannot be 0.
        startForeground(100, notification);

        // lancement du thread reseau
        m_thread = new TrafficThread();
        m_thread.start();

        m_fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // lancement differe de la demande de localisation pour laisser le temps de connecter le socket
        Handler handler = new Handler();
        handler.postDelayed(() -> lancerLocalisation(), 1000 /*ms*/);

        m_isRunning = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // pas de binding propose
        return null;
    }

    @Override
    public void onDestroy() {
        // arret de la demande de localisation
        try {
            m_fusedLocationClient.removeLocationUpdates(m_locationCallback);
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        // arret du thread dedie
        m_thread.m_stop = true;

        m_isRunning = false;
    }

    private void lancerLocalisation() {
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