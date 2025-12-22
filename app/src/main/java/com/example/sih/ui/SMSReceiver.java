package com.example.sih.ui;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.ToneGenerator; // Changed import
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.telephony.SmsMessage;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.sih.db.SmsDatabase;
import com.example.sih.db.SmsEntity;
import com.example.sih.ui.MainActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SMSReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "sih_tracker_channel";

    @Override
    public void onReceive(Context context, Intent intent) {

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null || pdus.length == 0) return;

        StringBuilder fullMessageBuilder = new StringBuilder();
        String sender = "";

        for (Object pdu : pdus) {
            try {
                SmsMessage part = SmsMessage.createFromPdu((byte[]) pdu);
                if (part != null) {
                    fullMessageBuilder.append(part.getMessageBody());
                    if (sender.isEmpty()) {
                        sender = part.getOriginatingAddress();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        String fullMessage = fullMessageBuilder.toString();

        // Check for SOS Keywords
        boolean isSOS = fullMessage.toUpperCase().contains("SOS") ||
                fullMessage.toUpperCase().contains("HELP") ||
                fullMessage.toUpperCase().contains("DANGER");

        String battery = extract(fullMessage, "Battery:");
        String signal = extract(fullMessage, "Signal:");
        String latStr = extract(fullMessage, "Lat:");
        String lonStr = extract(fullMessage, "Lon:");

        // --- GEOFENCE CHECK (Outside India) ---
        try {
            double lat = Double.parseDouble(latStr);
            double lon = Double.parseDouble(lonStr);
            // Approximate India Bounds
            if (lat < 6.0 || lat > 38.0 || lon < 68.0 || lon > 98.0) {
                fullMessage += " #DANGER: OUTSIDE GEOFENCE";
                isSOS = true; // Force SOS alert for geofence breach
            }
        } catch (Exception ignored) {}

        String time = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(new Date());

        // Update Live UI if open
        try {
            MainActivity.battery.setValue(battery);
            MainActivity.signal.setValue(signal);
            MainActivity.lat.setValue(latStr);
            MainActivity.lon.setValue(lonStr);
            String current = MainActivity.smsLog.getValue();
            MainActivity.smsLog.setValue(current + "\nSender: " + sender + "\nMsg: " + fullMessage);
        } catch (Exception ignored) { }

        String finalSender = sender;
        String finalMsg = fullMessage;
        new Thread(() -> {
            SmsEntity entity = new SmsEntity(0, finalSender, finalMsg, latStr, lonStr, battery, signal, time);
            SmsDatabase.getDatabase(context).smsDao().insertMessage(entity);
        }).start();

        // --- TRIGGER ALERTS ---
        if (isSOS) {
            triggerSOSVibration(context); // Physical Vibration
            playSOSSound();               // NEW: SOS Morse Code Sound
            showNotification(context, "🚨 SOS ALERT RECEIVED!", "Sender: " + sender + "\n" + fullMessage);
        } else {
            showNotification(context, "Tracking Update", "Loc: " + latStr + ", " + lonStr + " | Bat: " + battery);
        }

        // Auto-open Map if app is in foreground and location is valid
        if (!latStr.equals("N/A") && !lonStr.equals("N/A")) {
            Intent mapIntent = new Intent(context, MapActivity.class);
            mapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mapIntent.putExtra("lat", latStr);
            mapIntent.putExtra("lon", lonStr);
            context.startActivity(mapIntent);
        }
    }

    // --- NEW: PLAY SOS MORSE CODE SOUND ---
    private void playSOSSound() {
        try {
            ToneGenerator toneGen = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            // SOS Pattern: ... --- ...
            // S (...)
            toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200); // Short
            delay(300, () -> toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200));
            delay(600, () -> toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200));

            // O (---) - Using a different tone type for distinction if desired, or just longer duration
            delay(1000, () -> toneGen.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 600)); // Long
            delay(1700, () -> toneGen.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 600));
            delay(2400, () -> toneGen.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 600));

            // S (...)
            delay(3200, () -> toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200));
            delay(3500, () -> toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200));
            delay(3800, () -> {
                toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200);
                // Cleanup after sequence finishes
                new Handler(Looper.getMainLooper()).postDelayed(toneGen::release, 500);
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void delay(long millis, Runnable action) {
        new Handler(Looper.getMainLooper()).postDelayed(action, millis);
    }

    private void triggerSOSVibration(Context context) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            // Aggressive SOS Pattern
            long[] pattern = {0, 300, 100, 300, 100, 1000, 100, 300, 100, 300};

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(pattern, -1);
            }
        }
    }

    private void showNotification(Context context, String title, String content) {
        createNotificationChannel(context);

        Intent intent = new Intent(context, MapActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        try {
            NotificationManagerCompat.from(context).notify((int) System.currentTimeMillis(), builder.build());
        } catch (SecurityException e) {
            // Permission might not be granted on Android 13+
        }
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Tracking Alerts";
            String description = "Notifications for incoming location SMS";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.enableVibration(true);

            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private String extract(String text, String key) {
        if (!text.contains(key)) return "N/A";
        try {
            String[] parts = text.split(key);
            String after = parts[1].trim();
            return after.split("[\\s\\n]+")[0].trim();
        } catch (Exception e) {
            return "N/A";
        }
    }
}