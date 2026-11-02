package dev.ertu.quietfilter;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class HistoryEntry {
    String id = UUID.randomUUID().toString();
    String packageName;
    String appLabel;
    String title;
    String text;
    String ruleName;
    long timestamp;

    JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("id", id)
                .put("packageName", packageName)
                .put("appLabel", appLabel)
                .put("title", title)
                .put("text", text)
                .put("ruleName", ruleName)
                .put("timestamp", timestamp);
    }

    static HistoryEntry fromJson(JSONObject json) {
        HistoryEntry entry = new HistoryEntry();
        entry.packageName = json.optString("packageName", "");
        entry.appLabel = json.optString("appLabel", entry.packageName);
        entry.title = json.optString("title", "");
        entry.text = json.optString("text", "");
        entry.ruleName = json.optString("ruleName", "Unknown rule");
        entry.timestamp = json.optLong("timestamp", 0);
        entry.id = json.optString("id", legacyId(entry));
        return entry;
    }

    private static String legacyId(HistoryEntry entry) {
        String value = entry.packageName + "\n" + entry.title + "\n" + entry.text + "\n"
                + entry.ruleName + "\n" + entry.timestamp;
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
