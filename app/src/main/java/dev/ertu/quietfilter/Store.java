package dev.ertu.quietfilter;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class Store {
    private static final String PREFS = "quiet_filter_data";
    private static final String RULES = "rules";
    private static final String HISTORY = "history";
    private static final String KNOWN_APPS = "known_apps";
    private static final String BLOCK_COUNT = "block_count";
    private static final String BLOCK_SOUND = "block_sound";
    private static final String READ_WHATSAPP = "read_whatsapp";
    private static final int MAX_HISTORY = 500;
    private static final long HISTORY_LIFETIME_MS = 30L * 24 * 60 * 60 * 1000;
    private static final Object LOCK = new Object();
    private static List<Rule> cachedRules;

    private Store() {}

    static List<Rule> getRules(Context context) {
        synchronized (LOCK) {
            if (cachedRules != null) return new ArrayList<>(cachedRules);
            List<Rule> result = new ArrayList<>();
            try {
                JSONArray json = new JSONArray(prefs(context).getString(RULES, "[]"));
                for (int i = 0; i < json.length(); i++) {
                    JSONObject item = json.optJSONObject(i);
                    if (item != null) result.add(Rule.fromJson(item));
                }
            } catch (JSONException ignored) { }
            cachedRules = result;
            return new ArrayList<>(result);
        }
    }

    static void saveRule(Context context, Rule rule) {
        synchronized (LOCK) {
            List<Rule> rules = getRules(context);
            boolean replaced = false;
            for (int i = 0; i < rules.size(); i++) {
                if (rules.get(i).id.equals(rule.id)) {
                    rules.set(i, rule);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) rules.add(rule);
            saveRules(context, rules);
        }
    }

    static void deleteRule(Context context, String id) {
        synchronized (LOCK) {
            List<Rule> rules = getRules(context);
            rules.removeIf(rule -> rule.id.equals(id));
            saveRules(context, rules);
        }
    }

    private static void saveRules(Context context, List<Rule> rules) {
        JSONArray json = new JSONArray();
        try {
            for (Rule rule : rules) json.put(rule.toJson());
            prefs(context).edit().putString(RULES, json.toString()).apply();
            cachedRules = new ArrayList<>(rules);
        } catch (JSONException ignored) { }
    }

    static void addHistory(Context context, HistoryEntry entry) {
        synchronized (LOCK) {
            List<HistoryEntry> entries = getHistory(context);
            entries.add(0, entry);
            saveHistory(context, entries);
        }
    }

    static List<HistoryEntry> getHistory(Context context) {
        synchronized (LOCK) {
            List<HistoryEntry> result = new ArrayList<>();
            long oldest = System.currentTimeMillis() - HISTORY_LIFETIME_MS;
            try {
                JSONArray json = new JSONArray(prefs(context).getString(HISTORY, "[]"));
                for (int i = 0; i < json.length() && result.size() < MAX_HISTORY; i++) {
                    JSONObject item = json.optJSONObject(i);
                    if (item == null) continue;
                    HistoryEntry entry = HistoryEntry.fromJson(item);
                    if (entry.timestamp >= oldest) result.add(entry);
                }
            } catch (JSONException ignored) { }
            return result;
        }
    }

    static void clearHistory(Context context) {
        prefs(context).edit().remove(HISTORY).apply();
    }

    static int incrementBlockCount(Context context) {
        synchronized (LOCK) {
            SharedPreferences preferences = prefs(context);
            int count = preferences.getInt(BLOCK_COUNT, 0) + 1;
            preferences.edit().putInt(BLOCK_COUNT, count).commit();
            return count;
        }
    }

    static int getBlockCount(Context context) {
        return prefs(context).getInt(BLOCK_COUNT, 0);
    }

    static void clearBlockCount(Context context) {
        prefs(context).edit().remove(BLOCK_COUNT).commit();
    }

    static int acknowledgeBlockCount(Context context, int acknowledgedCount) {
        synchronized (LOCK) {
            SharedPreferences preferences = prefs(context);
            int remaining = Math.max(0,
                    preferences.getInt(BLOCK_COUNT, 0) - Math.max(0, acknowledgedCount));
            SharedPreferences.Editor edit = preferences.edit();
            if (remaining == 0) edit.remove(BLOCK_COUNT); else edit.putInt(BLOCK_COUNT, remaining);
            edit.commit();
            return remaining;
        }
    }

    static boolean isBlockSoundEnabled(Context context) {
        return prefs(context).getBoolean(BLOCK_SOUND, false);
    }

    static void setBlockSoundEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(BLOCK_SOUND, enabled).apply();
    }

    static boolean isReadWhatsAppEnabled(Context context) {
        return prefs(context).getBoolean(READ_WHATSAPP, false);
    }

    static void setReadWhatsAppEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(READ_WHATSAPP, enabled).apply();
    }

    static void deleteHistory(Context context, String id) {
        synchronized (LOCK) {
            List<HistoryEntry> entries = getHistory(context);
            entries.removeIf(entry -> entry.id.equals(id));
            saveHistory(context, entries);
        }
    }

    static void rememberApp(Context context, String packageName, String label) {
        if (packageName == null || packageName.isEmpty()) return;
        synchronized (LOCK) {
            try {
                JSONObject apps = new JSONObject(prefs(context).getString(KNOWN_APPS, "{}"));
                String safeLabel = label == null || label.isEmpty() ? packageName : label;
                if (safeLabel.equals(apps.optString(packageName))) return;
                apps.put(packageName, safeLabel);
                prefs(context).edit().putString(KNOWN_APPS, apps.toString()).apply();
            } catch (JSONException ignored) { }
        }
    }

    static Map<String, String> getKnownApps(Context context) {
        synchronized (LOCK) {
            Map<String, String> result = new LinkedHashMap<>();
            try {
                JSONObject apps = new JSONObject(prefs(context).getString(KNOWN_APPS, "{}"));
                Iterator<String> packages = apps.keys();
                while (packages.hasNext()) {
                    String packageName = packages.next();
                    result.put(packageName, apps.optString(packageName, packageName));
                }
            } catch (JSONException ignored) { }
            return result;
        }
    }

    static String exportConfig(Context context) throws JSONException {
        JSONArray rules = new JSONArray();
        for (Rule rule : getRules(context)) rules.put(rule.toJson());
        return new JSONObject()
                .put("format", "quietfilter")
                .put("version", 1)
                .put("rules", rules)
                .toString(2);
    }

    static List<Rule> parseConfig(String source) throws JSONException {
        JSONObject root = new JSONObject(source);
        if (!"quietfilter".equals(root.optString("format")) || root.optInt("version") != 1) {
            throw new JSONException("Unsupported QuietFilter format");
        }
        JSONArray json = root.getJSONArray("rules");
        List<Rule> rules = new ArrayList<>();
        for (int i = 0; i < json.length(); i++) {
            JSONObject item = json.optJSONObject(i);
            if (item == null) continue;
            Rule rule = Rule.fromJson(item);
            if (!rule.conditions.isEmpty()) rules.add(rule);
        }
        return rules;
    }

    static void importRules(Context context, List<Rule> imported, boolean replace) {
        synchronized (LOCK) {
            List<Rule> result = replace ? new ArrayList<>() : getRules(context);
            if (!replace) {
                for (Rule rule : imported) rule.id = UUID.randomUUID().toString();
            }
            result.addAll(imported);
            saveRules(context, result);
        }
    }

    private static void saveHistory(Context context, List<HistoryEntry> entries) {
        JSONArray json = new JSONArray();
        long oldest = System.currentTimeMillis() - HISTORY_LIFETIME_MS;
        try {
            int written = 0;
            for (HistoryEntry entry : entries) {
                if (entry.timestamp >= oldest && written++ < MAX_HISTORY) json.put(entry.toJson());
            }
            // NotificationListenerService may be the only active app component. Persist the
            // entry before returning so a background process stop cannot lose a recorded block.
            prefs(context).edit().putString(HISTORY, json.toString()).commit();
        } catch (JSONException ignored) { }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
