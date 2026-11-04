package dev.ertu.quietfilter;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.Toast;

public final class OtpCopyReceiver extends BroadcastReceiver {
    static final String EXTRA_OTP = "otp";
    static final String EXTRA_NOTIFICATION_KEY = "notification_key";

    @Override
    public void onReceive(Context context, Intent intent) {
        String otp = intent == null ? null : intent.getStringExtra(EXTRA_OTP);
        if (otp == null || !otp.matches("\\d{6}")) return;
        ClipboardManager clipboard = context.getSystemService(ClipboardManager.class);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("Verification code", otp));
        FilterNotificationService.cancelSourceNotification(
                intent.getStringExtra(EXTRA_NOTIFICATION_KEY));
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        if (notifications != null) notifications.cancel(FilterNotificationService.OTP_NOTIFICATION_ID);
        if (Build.VERSION.SDK_INT < 33) {
            Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show();
        }
    }
}
