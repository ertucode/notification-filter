package dev.ertu.quietfilter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class Rule {
    String id = UUID.randomUUID().toString();
    String name = "";
    String packageName = "";
    String appLabel = "All apps";
    boolean enabled = true;
    boolean matchAll = true;
    final List<Condition> conditions = new ArrayList<>();

    boolean matches(String sourcePackage, String title, String text) {
        if (!enabled || (!packageName.isEmpty() && !packageName.equals(sourcePackage)) || conditions.isEmpty()) {
            return false;
        }
        for (Condition condition : conditions) {
            boolean matched = condition.matches(title, text);
            if (matchAll && !matched) return false;
            if (!matchAll && matched) return true;
        }
        return matchAll;
    }

    JSONObject toJson() throws JSONException {
        JSONArray conditionJson = new JSONArray();
        for (Condition condition : conditions) conditionJson.put(condition.toJson());
        return new JSONObject()
                .put("id", id)
                .put("name", name)
                .put("packageName", packageName)
                .put("appLabel", appLabel)
                .put("enabled", enabled)
                .put("matchAll", matchAll)
                .put("conditions", conditionJson);
    }

    static Rule fromJson(JSONObject json) {
        Rule rule = new Rule();
        rule.id = json.optString("id", rule.id);
        rule.name = json.optString("name", "Unnamed rule");
        rule.packageName = json.optString("packageName", "");
        rule.appLabel = json.optString("appLabel", rule.packageName.isEmpty() ? "All apps" : rule.packageName);
        rule.enabled = json.optBoolean("enabled", true);
        rule.matchAll = json.optBoolean("matchAll", true);
        JSONArray conditions = json.optJSONArray("conditions");
        if (conditions != null) {
            for (int i = 0; i < conditions.length(); i++) {
                JSONObject condition = conditions.optJSONObject(i);
                if (condition != null) rule.conditions.add(Condition.fromJson(condition));
            }
        }
        return rule;
    }
}
