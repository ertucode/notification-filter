package dev.ertu.quietfilter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class FilterNotificationService extends NotificationListenerService {
    private static final String SUMMARY_CHANNEL = "block_summary";
    private static final int SUMMARY_NOTIFICATION_ID = 1001;
    private static volatile FilterNotificationService activeInstance;
    private ToneGenerator toneGenerator;

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
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
        super.onDestroy();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification notification) {
        rememberApp(notification);
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
