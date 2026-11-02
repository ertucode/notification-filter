package dev.ertu.quietfilter;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

final class Condition {
    static final String FIELD_ANY = "any";
    static final String FIELD_TITLE = "title";
    static final String FIELD_TEXT = "text";

    static final String OP_CONTAINS = "contains";
    static final String OP_NOT_CONTAINS = "not_contains";
    static final String OP_EQUALS = "equals";
    static final String OP_REGEX = "regex";

    String field = FIELD_ANY;
    String operator = OP_CONTAINS;
    String value = "";
    private Pattern cachedPattern;
    private String cachedPatternValue;
    private boolean cachedPatternInvalid;

    boolean matches(String title, String text) {
        String candidate;
        if (FIELD_TITLE.equals(field)) {
            candidate = title;
        } else if (FIELD_TEXT.equals(field)) {
            candidate = text;
        } else {
            candidate = title + "\n" + text;
        }

        if (OP_REGEX.equals(operator)) {
            try {
                if (!value.equals(cachedPatternValue)) {
                    cachedPattern = Pattern.compile(value, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                    cachedPatternValue = value;
                    cachedPatternInvalid = false;
                }
                return !cachedPatternInvalid && cachedPattern.matcher(candidate).find();
            } catch (PatternSyntaxException ignored) {
                cachedPatternValue = value;
                cachedPatternInvalid = true;
                return false;
            }
        }

        String left = candidate.toLowerCase(Locale.ROOT);
        String right = value.toLowerCase(Locale.ROOT);
        if (OP_EQUALS.equals(operator)) return left.equals(right);
        if (OP_NOT_CONTAINS.equals(operator)) return !left.contains(right);
        return left.contains(right);
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("field", field)
                .put("operator", operator)
                .put("value", value);
    }

    static Condition fromJson(JSONObject json) {
        Condition condition = new Condition();
        condition.field = json.optString("field", FIELD_ANY);
        condition.operator = json.optString("operator", OP_CONTAINS);
        condition.value = json.optString("value", "");
        return condition;
    }
}
