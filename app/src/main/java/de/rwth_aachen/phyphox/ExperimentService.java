package de.rwth_aachen.phyphox;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

/**
 * Foreground Service that keeps experiments running when the app is in the background.
 * This allows data collection to continue even when the screen is off.
 */
public class ExperimentService extends Service {

    private static final String TAG = "ExperimentService";
    private static final String CHANNEL_ID = "phyphox_experiment_channel";
    private static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_START = "de.rwth_aachen.phyphox.action.START";
    public static final String ACTION_STOP = "de.rwth_aachen.phyphox.action.STOP";
    public static final String EXTRA_EXPERIMENT_TITLE = "experiment_title";

    private final IBinder binder = new ExperimentBinder();
    private PowerManager.WakeLock wakeLock;
    private boolean isRunning = false;
    private String experimentTitle = "";

    // Callback interface for communicating with the Activity
    public interface ExperimentServiceCallback {
        void onServiceStopped();
    }

    private ExperimentServiceCallback callback;

    public class ExperimentBinder extends Binder {
        public ExperimentService getService() {
            return ExperimentService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        acquireWakeLock();
        Log.d(TAG, "ExperimentService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START.equals(action)) {
                experimentTitle = intent.getStringExtra(EXTRA_EXPERIMENT_TITLE);
                if (experimentTitle == null) {
                    experimentTitle = getString(R.string.app_name);
                }
                startForegroundService();
                isRunning = true;
            } else if (ACTION_STOP.equals(action)) {
                stopExperimentService();
            }
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void setCallback(ExperimentServiceCallback callback) {
        this.callback = callback;
    }

    public boolean isRunning() {
        return isRunning;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.notification_channel_description));
            channel.setShowBadge(false);
            channel.setSound(null, null);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void startForegroundService() {
        Notification notification = createNotification();

        // For Android 14+ (API 34+), we need to specify the foreground service type
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            );
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        Log.d(TAG, "Foreground service started");
    }

    private Notification createNotification() {
        // Intent to open the app when notification is tapped
        Intent notificationIntent = new Intent(this, Experiment.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        
        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                pendingIntentFlags
        );

        // Stop action
        Intent stopIntent = new Intent(this, ExperimentService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                1,
                stopIntent,
                pendingIntentFlags
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_experiment_running))
                .setContentText(experimentTitle)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .addAction(R.drawable.ic_stop, getString(R.string.stop), stopPendingIntent);

        return builder.build();
    }

    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "phyphox:experiment_wakelock"
            );
            wakeLock.acquire();
            Log.d(TAG, "Wake lock acquired");
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
            Log.d(TAG, "Wake lock released");
        }
    }

    public void stopExperimentService() {
        isRunning = false;
        // Only call callback if it's still set (won't be set if app returned to foreground)
        ExperimentServiceCallback cb = callback;
        callback = null; // Clear to prevent double-calls
        if (cb != null) {
            cb.onServiceStopped();
        }
        stopForeground(true);
        stopSelf();
        Log.d(TAG, "ExperimentService stopped");
    }

    public void updateNotification(String title) {
        this.experimentTitle = title;
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseWakeLock();
        isRunning = false;
        Log.d(TAG, "ExperimentService destroyed");
    }

    // Static helper methods to start/stop the service
    public static void start(Context context, String experimentTitle) {
        Intent intent = new Intent(context, ExperimentService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_EXPERIMENT_TITLE, experimentTitle);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, ExperimentService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }
}
