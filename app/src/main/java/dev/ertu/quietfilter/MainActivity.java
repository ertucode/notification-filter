package dev.ertu.quietfilter;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int IMPORT_CONFIG = 10;
    private static final int SAVE_CONFIG = 11;
    private static final int NOTIFICATION_PERMISSION = 12;
    private LinearLayout content;
    private Button historyTab;
    private Button rulesTab;
    private boolean showingHistory = true;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildScreen();
        requestSummaryPermission();
        if (state == null) content.post(() -> handleExternalImport(getIntent()));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        FilterNotificationService.clearBlockSummary(this);
        content.post(() -> handleExternalImport(intent));
    }

    @Override
    protected void onResume() {
        super.onResume();
        FilterNotificationService.clearBlockSummary(this);
        render();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(250, 250, 247));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(this, 20), Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 10));
        TextView title = Ui.heading(this, "QuietFilter");
        top.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button access = new Button(this);
        access.setText("Grant access");
        Ui.stylePrimary(access);
        access.setOnClickListener(view -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        top.addView(access);
        root.addView(top);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 8));
        historyTab = tabButton("Blocked");
        rulesTab = tabButton("Rules");
        tabs.addView(historyTab, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        tabs.addView(rulesTab, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        root.addView(tabs);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        Ui.respectSystemBars(root);
        setContentView(root);
    }

    private Button tabButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        Ui.styleSecondary(button);
        button.setOnClickListener(view -> {
            showingHistory = view == historyTab;
            render();
        });
        return button;
    }

    private void render() {
        if (content == null) return;
        Ui.styleTab(historyTab, showingHistory);
        Ui.styleTab(rulesTab, !showingHistory);
        content.removeAllViews();
        if (!hasAccess()) {
            TextView warning = Ui.text(this,
                    "Notification access is off. Grant access so matching notifications can be removed.", 15);
            warning.setTextColor(Color.rgb(145, 72, 30));
            warning.setPadding(0, 0, 0, Ui.dp(this, 16));
            content.addView(warning);
        }
        if (!Settings.canDrawOverlays(this)) {
            LinearLayout overlayWarning = new LinearLayout(this);
            overlayWarning.setGravity(Gravity.CENTER_VERTICAL);
            overlayWarning.setPadding(0, 0, 0, Ui.dp(this, 16));
            TextView message = Ui.text(this,
                    "Allow display over other apps for right-side OTP pop-ups.", 14);
            message.setTextColor(Color.rgb(145, 72, 30));
            overlayWarning.addView(message, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            Button allow = new Button(this);
            allow.setText("Allow");
            Ui.styleSecondary(allow);
            allow.setOnClickListener(view -> openOverlaySettings());
            overlayWarning.addView(allow);
            content.addView(overlayWarning);
        }
        if (showingHistory) renderHistory(); else renderRules();
    }

    private void renderHistory() {
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(Ui.heading(this, "Blocked history"),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button clear = new Button(this);
        clear.setText("Clear");
        Ui.styleSecondary(clear);
        clear.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle("Clear blocked history?")
                .setMessage("Rules will not be affected.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    Store.clearHistory(this);
                    render();
                }).show());
        heading.addView(clear);
        content.addView(heading);

        LinearLayout soundRow = new LinearLayout(this);
        soundRow.setGravity(Gravity.CENTER_VERTICAL);
        soundRow.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 8));
        LinearLayout soundText = new LinearLayout(this);
        soundText.setOrientation(LinearLayout.VERTICAL);
        soundText.addView(Ui.heading(this, "Block sound"));
        TextView soundDescription = Ui.text(this,
                "Play a short, quiet sound whenever a notification is blocked.", 13);
        soundDescription.setTextColor(Color.DKGRAY);
        soundText.addView(soundDescription);
        soundRow.addView(soundText, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Switch blockSound = new Switch(this);
        blockSound.setChecked(Store.isBlockSoundEnabled(this));
        blockSound.setContentDescription("Play a sound when a notification is blocked");
        blockSound.setOnCheckedChangeListener((button, checked) ->
                Store.setBlockSoundEnabled(this, checked));
        soundRow.addView(blockSound);
        content.addView(soundRow);

        List<HistoryEntry> entries = Store.getHistory(this);
        if (entries.isEmpty()) {
            TextView empty = Ui.text(this, "Removed notifications will appear here for 30 days.", 15);
            empty.setPadding(0, Ui.dp(this, 24), 0, 0);
            content.addView(empty);
            return;
        }
        DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        for (HistoryEntry entry : entries) {
            LinearLayout card = verticalCard();
            String title = TextUtils.isEmpty(entry.title) ? "(No title)" : entry.title;
            card.addView(Ui.heading(this, title));
            TextView source = Ui.text(this, entry.appLabel + "  ·  " + format.format(new Date(entry.timestamp)), 13);
            source.setTextColor(Color.DKGRAY);
            card.addView(source);
            if (!TextUtils.isEmpty(entry.text)) {
                TextView body = Ui.text(this, entry.text, 15);
                body.setMaxLines(4);
                body.setEllipsize(TextUtils.TruncateAt.END);
                body.setPadding(0, Ui.dp(this, 8), 0, 0);
                card.addView(body);
            }
            TextView reason = Ui.text(this, "Blocked by “" + entry.ruleName + "”", 12);
            reason.setTextColor(Color.rgb(49, 92, 73));
            reason.setPadding(0, Ui.dp(this, 8), 0, 0);
            card.addView(reason);

            LinearLayout actions = new LinearLayout(this);
            actions.setGravity(Gravity.END);
            Button createRule = new Button(this);
            createRule.setText("Create rule");
            Ui.styleSecondary(createRule);
            createRule.setOnClickListener(view -> createRuleFrom(
                    entry.packageName, entry.appLabel, entry.title, entry.text));
            actions.addView(createRule);
            Button delete = new Button(this);
            delete.setText("Delete");
            Ui.styleDanger(delete);
            delete.setOnClickListener(view -> {
                Store.deleteHistory(this, entry.id);
                render();
            });
            actions.addView(delete);
            card.addView(actions);
            content.addView(card);
            content.addView(Ui.divider(this));
        }
    }

    private void renderRules() {
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(Ui.heading(this, "Blocking rules"),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button transfer = new Button(this);
        transfer.setText("Transfer");
        Ui.styleSecondary(transfer);
        transfer.setOnClickListener(view -> showTransfer());
        heading.addView(transfer);
        Button add = new Button(this);
        add.setText("Add rule");
        Ui.stylePrimary(add);
        add.setOnClickListener(view -> startActivity(new Intent(this, RuleEditorActivity.class)));
        heading.addView(add);
        content.addView(heading);

        Button fromNotification = new Button(this);
        fromNotification.setText("Create rule from notification");
        Ui.styleSecondary(fromNotification);
        fromNotification.setOnClickListener(view -> chooseNotification());
        content.addView(fromNotification, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        List<Rule> rules = Store.getRules(this);
        if (rules.isEmpty()) {
            TextView empty = Ui.text(this, "No rules yet. Add one to start blocking notifications.", 15);
            empty.setPadding(0, Ui.dp(this, 24), 0, 0);
            content.addView(empty);
            return;
        }
        for (Rule rule : rules) {
            LinearLayout row = verticalCard();
            LinearLayout firstLine = new LinearLayout(this);
            firstLine.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = Ui.heading(this, rule.name);
            firstLine.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            Switch enabled = new Switch(this);
            enabled.setChecked(rule.enabled);
            enabled.setContentDescription("Enable " + rule.name);
            enabled.setOnCheckedChangeListener((button, checked) -> {
                rule.enabled = checked;
                Store.saveRule(this, rule);
            });
            firstLine.addView(enabled);
            row.addView(firstLine);
            row.addView(Ui.text(this, summary(rule), 14));
            row.setOnClickListener(view -> {
                Intent intent = new Intent(this, RuleEditorActivity.class);
                intent.putExtra("rule_id", rule.id);
                startActivity(intent);
            });
            content.addView(row);
            content.addView(Ui.divider(this));
        }
    }

    private String summary(Rule rule) {
        String app = rule.packageName.isEmpty() ? "All apps" : rule.appLabel;
        int count = rule.conditions.size();
        return app + " · " + count + (count == 1 ? " condition" : " conditions")
                + (count > 1 ? (rule.matchAll ? " · match all" : " · match any") : "");
    }

    private void chooseNotification() {
        List<FilterNotificationService.NotificationSnapshot> notifications =
                FilterNotificationService.activeNotificationsForRules();
        if (notifications.isEmpty()) {
            Toast.makeText(this, hasAccess()
                    ? "No current notifications available"
                    : "Notification access is not active", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] choices = new String[notifications.size()];
        for (int i = 0; i < notifications.size(); i++) {
            FilterNotificationService.NotificationSnapshot item = notifications.get(i);
            String detail = item.text.isEmpty() ? item.title : item.text;
            detail = detail.replace('\n', ' ').trim();
            if (detail.isEmpty()) detail = "(No readable title or message)";
            if (detail.length() > 100) detail = detail.substring(0, 97) + "…";
            choices[i] = item.appLabel + "\n" + detail;
        }
        new AlertDialog.Builder(this)
                .setTitle("Choose a notification")
                .setItems(choices, (dialog, which) -> createRuleFrom(notifications.get(which)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createRuleFrom(FilterNotificationService.NotificationSnapshot notification) {
        createRuleFrom(notification.packageName, notification.appLabel,
                notification.title, notification.text);
    }

    private void createRuleFrom(String packageName, String appLabel, String title, String text) {
        boolean useMessage = text != null && !text.isEmpty();
        Intent intent = new Intent(this, RuleEditorActivity.class);
        intent.putExtra(RuleEditorActivity.EXTRA_SOURCE_PACKAGE, packageName);
        intent.putExtra(RuleEditorActivity.EXTRA_SOURCE_APP_LABEL, appLabel);
        intent.putExtra(RuleEditorActivity.EXTRA_SOURCE_FIELD,
                useMessage ? Condition.FIELD_TEXT : Condition.FIELD_TITLE);
        intent.putExtra(RuleEditorActivity.EXTRA_SOURCE_TEXT,
                useMessage ? text : title);
        startActivity(intent);
    }

    private LinearLayout verticalCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 16));
        return card;
    }

    private boolean hasAccess() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        String component = new ComponentName(this, FilterNotificationService.class).flattenToString();
        return enabled != null && enabled.contains(component);
    }

    private void openOverlaySettings() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (RuntimeException error) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        }
    }

    private void requestSummaryPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION);
        }
    }

    private void showTransfer() {
        String[] choices = {"Import JSON", "Share JSON", "Save JSON file"};
        new AlertDialog.Builder(this)
                .setTitle("Transfer rules")
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) openImport();
                    else if (which == 1) shareConfig();
                    else saveConfig();
                })
                .show();
    }

    private void openImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                ConfigProvider.MIME_TYPE, "application/json", "application/octet-stream"
        });
        startActivityForResult(intent, IMPORT_CONFIG);
    }

    private void saveConfig() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(ConfigProvider.MIME_TYPE);
        intent.putExtra(Intent.EXTRA_TITLE, ConfigProvider.FILE_NAME);
        startActivityForResult(intent, SAVE_CONFIG);
    }

    private void shareConfig() {
        try {
            writeFile(ConfigProvider.file(this), Store.exportConfig(this));
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType(ConfigProvider.MIME_TYPE);
            send.putExtra(Intent.EXTRA_STREAM, ConfigProvider.uri(this));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, "Share QuietFilter rules"));
        } catch (Exception error) {
            Toast.makeText(this, "Could not share configuration", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == SAVE_CONFIG) {
            try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output == null) throw new Exception("No output");
                output.write(Store.exportConfig(this).getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, "Configuration saved", Toast.LENGTH_SHORT).show();
            } catch (Exception error) {
                Toast.makeText(this, "Could not save configuration", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == IMPORT_CONFIG) {
            try {
                String json = readConfig(uri);
                List<Rule> imported = Store.parseConfig(json);
                if (imported.isEmpty()) throw new JSONException("No rules");
                confirmImport(imported);
            } catch (Exception error) {
                Toast.makeText(this, "Invalid QuietFilter configuration", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void confirmImport(List<Rule> imported) {
        new AlertDialog.Builder(this)
                .setTitle("Import " + imported.size() + (imported.size() == 1 ? " rule?" : " rules?"))
                .setMessage("Add them to your rules or replace all current rules.")
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Replace", (dialog, which) -> finishImport(imported, true))
                .setPositiveButton("Add", (dialog, which) -> finishImport(imported, false))
                .show();
    }

    private void finishImport(List<Rule> imported, boolean replace) {
        Store.importRules(this, imported, replace);
        Toast.makeText(this, "Rules imported", Toast.LENGTH_SHORT).show();
        render();
    }

    @SuppressWarnings("deprecation")
    private void handleExternalImport(Intent intent) {
        if (intent == null) return;
        Uri uri = null;
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            uri = intent.getData();
        } else if (Intent.ACTION_SEND.equals(intent.getAction())) {
            uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        if (uri == null) return;
        intent.setAction(null);
        try {
            List<Rule> imported = Store.parseConfig(readConfig(uri));
            if (imported.isEmpty()) throw new JSONException("No rules");
            showingHistory = false;
            render();
            confirmImport(imported);
        } catch (Exception error) {
            Toast.makeText(this, "This is not a valid QuietFilter configuration", Toast.LENGTH_LONG).show();
        }
    }

    private String readConfig(Uri uri) throws Exception {
        try (InputStream input = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new Exception("No input");
            byte[] buffer = new byte[4096];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > 1024 * 1024) throw new Exception("File too large");
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void writeFile(java.io.File file, String content) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }
}
