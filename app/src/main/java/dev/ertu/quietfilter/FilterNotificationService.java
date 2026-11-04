package dev.ertu.quietfilter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FilterNotificationService extends NotificationListenerService {
    private static final String SUMMARY_CHANNEL = "block_summary";
    private static final String OTP_CHANNEL = "otp_popups";
    private static final int SUMMARY_NOTIFICATION_ID = 1001;
    static final int OTP_NOTIFICATION_ID = 1002;
    private static final int OTP_COPY_REQUEST_ID = 1003;
    private static final long OTP_POPUP_DURATION_MS = 12_000;
    private static final Pattern OTP_PATTERN = Pattern.compile("(?<!\\d)\\d{6}(?!\\d)");
    private static volatile FilterNotificationService activeInstance;
    private ToneGenerator toneGenerator;
    private final Handler otpHandler = new Handler(Looper.getMainLooper());
    private final Runnable dismissOtp = this::dismissOtpOverlay;
    private WindowManager windowManager;
    private View otpOverlay;
    private String lastOtpNotificationKey = "";
    private String lastOtp = "";

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        activeInstance = this;
        StatusBarNotification[] active = getActiveNotifications();
        if (active != null) {
            for (StatusBarNotification notification : active) rememberApp(notification);
        }
        int blockCount = Store.getBlockCount(this);
        if (blockCount > 0) showBlockSummary(blockCount);
    }

    @Override
    public void onListenerDisconnected() {
        activeInstance = null;
        super.onListenerDisconnected();
    }

    @Override
    public void onDestroy() {
        if (activeInstance == this) activeInstance = null;
        dismissOtpOverlay();
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
        super.onDestroy();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification notification) {
        rememberApp(notification);
        showOtpIfPresent(notification);
        List<Rule> rules = Store.getRules(this);
        for (Rule rule : rules) {
            if (matches(notification, rule)) {
                block(notification, rule);
                break;
            }
        }
    }

    static int countActiveMatches(String ruleId) {
        FilterNotificationService service = activeInstance;
        if (service == null) return -1;
        Rule rule = service.findRule(ruleId);
        if (rule == null) return 0;
        StatusBarNotification[] active = service.getActiveNotifications();
        if (active == null) return 0;
        int count = 0;
        for (StatusBarNotification notification : active) {
            if (service.matches(notification, rule)) count++;
        }
        return count;
    }

    static int blockActiveMatches(String ruleId) {
        FilterNotificationService service = activeInstance;
        if (service == null) return -1;
        Rule rule = service.findRule(ruleId);
        if (rule == null) return 0;
        StatusBarNotification[] active = service.getActiveNotifications();
        if (active == null) return 0;
        int count = 0;
        for (StatusBarNotification notification : active) {
            if (!service.matches(notification, rule)) continue;
            service.block(notification, rule);
            count++;
        }
        return count;
    }

    static List<NotificationSnapshot> activeNotificationsForRules() {
        FilterNotificationService service = activeInstance;
        List<NotificationSnapshot> result = new ArrayList<>();
        if (service == null) return result;
        StatusBarNotification[] active = service.getActiveNotifications();
        if (active == null) return result;
        for (StatusBarNotification notification : active) {
            if (notification == null || service.getPackageName().equals(notification.getPackageName())) continue;
            Notification raw = notification.getNotification();
            if (raw == null) continue;
            String title = notificationTitle(raw.extras).trim();
            String text = service.collectText(raw.extras).trim();
            result.add(new NotificationSnapshot(notification.getPackageName(),
                    service.appLabel(notification.getPackageName()), title, text,
                    notification.getPostTime()));
        }
        result.sort(Comparator.comparingLong((NotificationSnapshot item) -> item.timestamp).reversed());
        return result;
    }

    private Rule findRule(String id) {
        for (Rule rule : Store.getRules(this)) if (rule.id.equals(id)) return rule;
        return null;
    }

    private boolean matches(StatusBarNotification notification, Rule rule) {
        if (notification == null || getPackageName().equals(notification.getPackageName())) return false;
        Notification raw = notification.getNotification();
        if (raw == null) return false;
        return rule.matches(notification.getPackageName(),
                notificationTitle(raw.extras), collectText(raw.extras));
    }

    private void block(StatusBarNotification notification, Rule rule) {
        Notification raw = notification.getNotification();
        String title = notificationTitle(raw.extras);
        String text = collectText(raw.extras);
        HistoryEntry entry = new HistoryEntry();
        entry.packageName = notification.getPackageName();
        entry.appLabel = appLabel(notification.getPackageName());
        entry.title = title;
        entry.text = text;
        entry.ruleName = rule.name;
        entry.timestamp = System.currentTimeMillis();
        Store.addHistory(this, entry);
        cancelNotification(notification.getKey());
        showBlockSummary(Store.incrementBlockCount(this));
        playBlockSound();
    }

    static void clearBlockSummary(Context context) {
        Store.clearBlockCount(context);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(SUMMARY_NOTIFICATION_ID);
    }

    private void showBlockSummary(int count) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(SUMMARY_CHANNEL,
                "Blocked notification summary", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Persistent count of notifications blocked since QuietFilter was opened");
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);

        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingOpen = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = count == 1 ? "1 notification blocked" : count + " notifications blocked";
        Notification summary = new Notification.Builder(this, SUMMARY_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(Color.rgb(49, 92, 73))
                .setContentTitle(title)
                .setContentText("Tap to view and clear this count")
                .setContentIntent(pendingOpen)
                .setCategory(Notification.CATEGORY_STATUS)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setNumber(count)
                .build();
        try {
            manager.notify(SUMMARY_NOTIFICATION_ID, summary);
        } catch (SecurityException ignored) {
            // Android 13+ users can decline notification permission; filtering still works.
        }
    }

    private void playBlockSound() {
        if (!Store.isBlockSoundEnabled(this)) return;
        try {
            if (toneGenerator == null) toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 35);
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 70);
        } catch (RuntimeException ignored) { }
    }

    private void showOtpIfPresent(StatusBarNotification notification) {
        if (notification == null || getPackageName().equals(notification.getPackageName())) return;
        Notification raw = notification.getNotification();
        if (raw == null) return;
        String otp = findOtp(notificationTitle(raw.extras) + "\n" + collectText(raw.extras));
        if (otp == null) return;
        if (notification.getKey().equals(lastOtpNotificationKey) && otp.equals(lastOtp)) return;
        lastOtpNotificationKey = notification.getKey();
        lastOtp = otp;

        String source = appLabel(notification.getPackageName());
        String sender = notificationSender(raw, source);
        if (Settings.canDrawOverlays(this)
                && showOtpOverlay(otp, source, sender, notification.getKey())) return;

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(OTP_CHANNEL,
                "OTP pop-ups", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Shows detected six-digit codes at the top of the screen");
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);

        Intent copy = new Intent(this, OtpCopyReceiver.class)
                .putExtra(OtpCopyReceiver.EXTRA_OTP, otp)
                .putExtra(OtpCopyReceiver.EXTRA_NOTIFICATION_KEY, notification.getKey());
        PendingIntent pendingCopy = PendingIntent.getBroadcast(this, OTP_COPY_REQUEST_ID, copy,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String displaySource = sender.isEmpty() ? source : sender;
        RemoteViews popupView = new RemoteViews(getPackageName(), R.layout.notification_otp);
        popupView.setTextViewText(R.id.otp_source, displaySource);
        popupView.setTextViewText(R.id.otp_code, otp);
        popupView.setTextViewText(R.id.otp_app, sender.isEmpty() ? "" : source);
        popupView.setViewVisibility(R.id.otp_app, sender.isEmpty() ? View.GONE : View.VISIBLE);
        popupView.setOnClickPendingIntent(R.id.otp_copy, pendingCopy);
        Notification.Builder popupBuilder = new Notification.Builder(this, OTP_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(Color.rgb(49, 92, 73))
                .setContentTitle("Verification code from " + displaySource)
                .setContentText(otp)
                .setStyle(new Notification.DecoratedCustomViewStyle())
                .setCustomContentView(popupView)
                .setCustomBigContentView(popupView)
                .setCustomHeadsUpContentView(popupView)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setPriority(Notification.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setTimeoutAfter(OTP_POPUP_DURATION_MS);
        if (!sender.isEmpty()) popupBuilder.setSubText(source);
        Notification popup = popupBuilder.build();
        try {
            manager.notify(OTP_NOTIFICATION_ID, popup);
        } catch (SecurityException ignored) {
            // Android 13+ users can decline notification permission; filtering still works.
        }
    }

    private boolean showOtpOverlay(String otp, String source, String sender,
                                   String notificationKey) {
        dismissOtpOverlay();
        windowManager = getSystemService(WindowManager.class);
        if (windowManager == null) return false;
        View popup = LayoutInflater.from(this).inflate(R.layout.otp_overlay, null);
        ((TextView) popup.findViewById(R.id.overlay_otp_source))
                .setText(sender.isEmpty() ? source : sender);
        ((TextView) popup.findViewById(R.id.overlay_otp_code)).setText(otp);
        TextView app = popup.findViewById(R.id.overlay_otp_app);
        app.setText(source);
        app.setVisibility(sender.isEmpty() ? View.GONE : View.VISIBLE);
        View copy = popup.findViewById(R.id.overlay_otp_copy);
        copy.setOnClickListener(view -> {
            ClipboardManager clipboard = getSystemService(ClipboardManager.class);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Verification code", otp));
                if (Build.VERSION.SDK_INT < 33) {
                    Toast.makeText(this, "Code copied", Toast.LENGTH_SHORT).show();
                }
            }
            cancelSourceNotification(notificationKey);
            dismissOtpOverlay();
        });
        popup.findViewById(R.id.overlay_otp_close)
                .setOnClickListener(view -> dismissOtpOverlay());
        ((SwipeDismissLayout) popup).setOnSwipeDismissListener(this::dismissOtpOverlay);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                Ui.dp(this, 156), Ui.dp(this, 124),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        params.x = Ui.dp(this, 12);
        try {
            windowManager.addView(popup, params);
            otpOverlay = popup;
            otpHandler.postDelayed(dismissOtp, OTP_POPUP_DURATION_MS);
            return true;
        } catch (RuntimeException ignored) {
            otpOverlay = null;
            return false;
        }
    }

    private void dismissOtpOverlay() {
        otpHandler.removeCallbacks(dismissOtp);
        if (otpOverlay != null && windowManager != null) {
            try {
                windowManager.removeView(otpOverlay);
            } catch (RuntimeException ignored) { }
        }
        otpOverlay = null;
    }

    static void cancelSourceNotification(String notificationKey) {
        FilterNotificationService service = activeInstance;
        if (service == null || notificationKey == null || notificationKey.isEmpty()) return;
        try {
            service.cancelNotification(notificationKey);
        } catch (SecurityException ignored) { }
    }

    private static String findOtp(String text) {
        Matcher matcher = OTP_PATTERN.matcher(text == null ? "" : text);
        return matcher.find() ? matcher.group() : null;
    }

    @SuppressWarnings("deprecation")
    private static String notificationSender(Notification notification, String appLabel) {
        Bundle extras = notification.extras;
        if (extras != null) {
            List<Notification.MessagingStyle.Message> messages =
                    Notification.MessagingStyle.Message.getMessagesFromBundleArray(
                            extras.getParcelableArray(Notification.EXTRA_MESSAGES));
            if (messages != null) {
                for (int i = messages.size() - 1; i >= 0; i--) {
                    CharSequence sender = messages.get(i).getSender();
                    if (sender != null && !sender.toString().trim().isEmpty()) {
                        return sender.toString().trim();
                    }
                }
            }
        }
        if (Notification.CATEGORY_MESSAGE.equals(notification.category)
                || Notification.CATEGORY_EMAIL.equals(notification.category)) {
            String title = notificationTitle(extras).trim();
            if (!title.isEmpty() && !title.equalsIgnoreCase(appLabel)) return title;
        }
        return "";
    }

    private String collectText(Bundle extras) {
        Set<String> parts = new LinkedHashSet<>();
        add(parts, value(extras, Notification.EXTRA_TEXT));
        add(parts, value(extras, Notification.EXTRA_BIG_TEXT));
        add(parts, value(extras, Notification.EXTRA_SUB_TEXT));
        add(parts, value(extras, Notification.EXTRA_SUMMARY_TEXT));
        add(parts, value(extras, Notification.EXTRA_INFO_TEXT));
        add(parts, value(extras, Notification.EXTRA_CONVERSATION_TITLE));
        CharSequence[] lines = extras == null ? null : extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) for (CharSequence line : lines) add(parts, line == null ? "" : line.toString());
        return TextUtils.join("\n", parts);
    }

    private static void add(Set<String> parts, String value) {
        if (!value.trim().isEmpty()) parts.add(value.trim());
    }

    private static String value(Bundle extras, String key) {
        if (extras == null) return "";
        CharSequence value = extras.getCharSequence(key);
        return value == null ? "" : value.toString();
    }

    private static String notificationTitle(Bundle extras) {
        String title = value(extras, Notification.EXTRA_TITLE);
        return title.trim().isEmpty() ? value(extras, Notification.EXTRA_TITLE_BIG) : title;
    }

    private String appLabel(String packageName) {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(packageName, 0);
            return getPackageManager().getApplicationLabel(info).toString();
        } catch (Exception ignored) {
            return packageName;
        }
    }

    private void rememberApp(StatusBarNotification notification) {
        if (notification == null || getPackageName().equals(notification.getPackageName())) return;
        String packageName = notification.getPackageName();
        Store.rememberApp(this, packageName, appLabel(packageName));
    }

    static final class NotificationSnapshot {
        final String packageName;
        final String appLabel;
        final String title;
        final String text;
        final long timestamp;

        NotificationSnapshot(String packageName, String appLabel, String title, String text, long timestamp) {
            this.packageName = packageName;
            this.appLabel = appLabel;
            this.title = title;
            this.text = text;
            this.timestamp = timestamp;
        }
    }
}
