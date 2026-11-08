package dev.ertu.quietfilter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

public final class ScreenDimmerService extends Service {
    static final int MODE_NONE = 0;
    static final int MODE_DARK = 1;
    static final int MODE_DARKER = 2;

    private static final String PREFS = "screen_dimmer";
    private static final String MODE = "mode";
    private static final String ACTION_TURN_OFF = "dev.ertu.quietfilter.TURN_DIMMER_OFF";
    private static final String CHANNEL = "screen_dimmer";
    private static final int NOTIFICATION_ID = 1005;
    private WindowManager windowManager;
    private View overlay;

    static int getMode(Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE).getInt(MODE, MODE_NONE);
    }

    static void setMode(Context context, int mode) {
        SharedPreferences.Editor edit = context.getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        if (mode == MODE_NONE) edit.remove(MODE); else edit.putInt(MODE, mode);
        edit.commit();
    }

    static String modeName(int mode) {
        if (mode == MODE_DARK) return "Dark";
        if (mode == MODE_DARKER) return "Darker";
        return "No";
    }

    static void applyMode(Context context, int mode) {
        setMode(context, mode);
        Intent service = new Intent(context, ScreenDimmerService.class);
        if (mode == MODE_NONE || !Settings.canDrawOverlays(context)) {
            context.stopService(service);
        } else {
            context.startForegroundService(service);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_TURN_OFF.equals(intent.getAction())) {
            setMode(this, MODE_NONE);
            stopSelf();
            return START_NOT_STICKY;
        }
        int mode = getMode(this);
        if (mode == MODE_NONE || !Settings.canDrawOverlays(this)) {
            setMode(this, MODE_NONE);
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTIFICATION_ID, foregroundNotification(mode));
        showOverlay(mode);
        return START_STICKY;
    }

    private Notification foregroundNotification(int mode) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Screen dimmer",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Keeps the screen darkening overlay active");
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setShowBadge(false);
        if (manager != null) manager.createNotificationChannel(channel);

        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingOpen = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent turnOff = new Intent(this, ScreenDimmerService.class).setAction(ACTION_TURN_OFF);
        PendingIntent pendingTurnOff = PendingIntent.getService(this, 0, turnOff,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_screen_dimmer)
                .setContentTitle("Screen dimmer: " + modeName(mode))
                .setContentText("Use the Quick Settings tile to change or turn it off")
                .setContentIntent(pendingOpen)
                .addAction(new Notification.Action.Builder(0, "Turn off", pendingTurnOff).build())
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void showOverlay(int mode) {
        if (overlay != null) {
            overlay.setBackgroundColor(overlayColor(mode));
            return;
        }
        windowManager = getSystemService(WindowManager.class);
        if (windowManager == null) return;
        View view = new View(this);
        view.setBackgroundColor(overlayColor(mode));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.FILL;
        if (Build.VERSION.SDK_INT >= 28) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }
        try {
            windowManager.addView(view, params);
            overlay = view;
        } catch (RuntimeException ignored) { }
    }

    private static int overlayColor(int mode) {
        return Color.argb(mode == MODE_DARKER ? 170 : 100, 0, 0, 0);
    }

    @Override
    public void onDestroy() {
        if (overlay != null && windowManager != null) {
            try {
                windowManager.removeView(overlay);
            } catch (RuntimeException ignored) { }
        }
        overlay = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
