package dev.ertu.quietfilter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class RuleEditorActivity extends Activity {
    static final String EXTRA_SOURCE_PACKAGE = "source_package";
    static final String EXTRA_SOURCE_APP_LABEL = "source_app_label";
    static final String EXTRA_SOURCE_FIELD = "source_field";
    static final String EXTRA_SOURCE_TEXT = "source_text";
    private static final String[] FIELD_LABELS = {"Title or message", "Title", "Message"};
    private static final String[] FIELD_VALUES = {Condition.FIELD_ANY, Condition.FIELD_TITLE, Condition.FIELD_TEXT};
    private static final String[] OP_LABELS = {"contains", "does not contain", "equals", "matches regex"};
    private static final String[] OP_VALUES = {Condition.OP_CONTAINS, Condition.OP_NOT_CONTAINS,
            Condition.OP_EQUALS, Condition.OP_REGEX};

    private EditText name;
    private Spinner app;
    private Spinner logic;
    private LinearLayout conditions;
    private final List<AppChoice> apps = new ArrayList<>();
    private final List<ConditionRow> rows = new ArrayList<>();
    private Rule rule;
    private boolean isNewRule;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        rule = findRule(getIntent().getStringExtra("rule_id"));
        if (rule == null) {
            isNewRule = true;
            rule = new Rule();
            Condition condition = new Condition();
            rule.packageName = valueOrEmpty(getIntent().getStringExtra(EXTRA_SOURCE_PACKAGE));
            rule.appLabel = valueOrDefault(getIntent().getStringExtra(EXTRA_SOURCE_APP_LABEL),
                    rule.packageName);
            String sourceText = getIntent().getStringExtra(EXTRA_SOURCE_TEXT);
            if (sourceText != null && !sourceText.trim().isEmpty()) {
                condition.value = sourceText.trim();
                condition.field = getIntent().getStringExtra(EXTRA_SOURCE_FIELD);
                if (!Condition.FIELD_TITLE.equals(condition.field)
                        && !Condition.FIELD_TEXT.equals(condition.field)) {
                    condition.field = Condition.FIELD_ANY;
                }
                condition.operator = Condition.OP_CONTAINS;
            }
            rule.conditions.add(condition);
        }
        loadApps();
        buildScreen();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(250, 250, 247));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        Button back = new Button(this);
        back.setText("Back");
        Ui.styleSecondary(back);
        back.setOnClickListener(view -> finish());
        bar.addView(back);
        TextView title = Ui.heading(this, rule.name.isEmpty() ? "New rule" : "Edit rule");
        title.setPadding(Ui.dp(this, 8), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), Ui.dp(this, 24));

        form.addView(label("Rule name"));
        name = new EditText(this);
        name.setHint("e.g. Block newsletters");
        name.setSingleLine(true);
        name.setText(rule.name);
        form.addView(name, fullWidth());

        form.addView(label("App"));
        app = new Spinner(this);
        ArrayAdapter<AppChoice> appAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, apps);
        appAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        app.setAdapter(appAdapter);
        app.setSelection(selectedApp());
        form.addView(app, fullWidth());
        TextView appHelp = Ui.text(this, "Choose All apps to match without an app restriction.", 12);
        form.addView(appHelp);

        form.addView(label("When multiple conditions"));
        logic = spinner(new String[]{"Match all", "Match any"});
        logic.setSelection(rule.matchAll ? 0 : 1);
        form.addView(logic, fullWidth());

        TextView conditionTitle = label("Conditions");
        form.addView(conditionTitle);
        conditions = new LinearLayout(this);
        conditions.setOrientation(LinearLayout.VERTICAL);
        form.addView(conditions, fullWidth());
        for (Condition condition : rule.conditions) addConditionRow(condition);

        Button addCondition = new Button(this);
        addCondition.setText("Add condition");
        Ui.styleSecondary(addCondition);
        addCondition.setOnClickListener(view -> addConditionRow(new Condition()));
        form.addView(addCondition, fullWidth());
        scroll.addView(form);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        actions.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 12));
        if (!rule.name.isEmpty()) {
            Button delete = new Button(this);
            delete.setText("Delete");
            Ui.styleDanger(delete);
            delete.setOnClickListener(view -> confirmDelete());
            actions.addView(delete);
        }
        Button save = new Button(this);
        save.setText("Save rule");
        Ui.stylePrimary(save);
        save.setOnClickListener(view -> save());
        actions.addView(save);
        root.addView(actions);
        Ui.respectSystemBars(root);
        setContentView(root);
    }

    private void addConditionRow(Condition condition) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 12));
        Spinner field = spinner(FIELD_LABELS);
        field.setSelection(indexOf(FIELD_VALUES, condition.field));
        card.addView(field, fullWidth());
        Spinner operator = spinner(OP_LABELS);
        operator.setSelection(indexOf(OP_VALUES, condition.operator));
        card.addView(operator, fullWidth());
        EditText value = new EditText(this);
        value.setHint("Text or pattern");
        value.setSingleLine(true);
        value.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        value.setText(condition.value);
        card.addView(value, fullWidth());
        Button remove = new Button(this);
        remove.setText("Remove condition");
        Ui.styleDanger(remove);
        ConditionRow row = new ConditionRow(card, field, operator, value);
        remove.setOnClickListener(view -> {
            if (rows.size() == 1) {
                Toast.makeText(this, "A rule needs at least one condition", Toast.LENGTH_SHORT).show();
                return;
            }
            rows.remove(row);
            conditions.removeView(card);
        });
        card.addView(remove);
        rows.add(row);
        conditions.addView(card);
        conditions.addView(Ui.divider(this));
    }

    private void save() {
        List<Condition> updated = new ArrayList<>();
        for (ConditionRow row : rows) {
            String value = row.value.getText().toString().trim();
            if (value.isEmpty()) {
                row.value.setError("Enter text to match");
                row.value.requestFocus();
                return;
            }
            String operator = OP_VALUES[row.operator.getSelectedItemPosition()];
            if (Condition.OP_REGEX.equals(operator)) {
                try {
                    Pattern.compile(value);
                } catch (PatternSyntaxException error) {
                    row.value.setError("Invalid regex: " + error.getDescription());
                    row.value.requestFocus();
                    return;
                }
            }
            Condition condition = new Condition();
            condition.field = FIELD_VALUES[row.field.getSelectedItemPosition()];
            condition.operator = operator;
            condition.value = value;
            updated.add(condition);
        }

        AppChoice selected = apps.get(app.getSelectedItemPosition());
        String enteredName = name.getText().toString().trim();
        rule.name = enteredName.isEmpty() ? "Block “" + updated.get(0).value + "”" : enteredName;
        rule.packageName = selected.packageName;
        rule.appLabel = selected.label;
        rule.matchAll = logic.getSelectedItemPosition() == 0;
        rule.conditions.clear();
        rule.conditions.addAll(updated);
        Store.saveRule(this, rule);
        if (isNewRule) offerToBlockExisting(); else finish();
    }

    private void offerToBlockExisting() {
        int count = FilterNotificationService.countActiveMatches(rule.id);
        if (count < 0) {
            Toast.makeText(this, "Rule saved. Notification access is not active.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (count == 0) {
            Toast.makeText(this, "Rule saved. No current notifications match.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(count + (count == 1 ? " current notification matches" : " current notifications match"))
                .setMessage("Block them now and add them to blocked history?")
                .setNegativeButton("Keep them", (dialog, which) -> finish())
                .setPositiveButton("Block now", (dialog, which) -> {
                    int blocked = FilterNotificationService.blockActiveMatches(rule.id);
                    Toast.makeText(this,
                            blocked < 0 ? "Notification access is not active" : "Blocked " + blocked,
                            Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Delete this rule?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    Store.deleteRule(this, rule.id);
                    finish();
                }).show();
    }

    private void loadApps() {
        apps.add(new AppChoice("All apps", ""));
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        PackageManager manager = getPackageManager();
        List<ResolveInfo> resolved = manager.queryIntentActivities(launcher, 0);
        Set<String> seen = new HashSet<>();
        for (ResolveInfo info : resolved) {
            String packageName = info.activityInfo.packageName;
            if (packageName.equals(getPackageName()) || !seen.add(packageName)) continue;
            apps.add(new AppChoice(info.loadLabel(manager).toString(), packageName));
        }
        for (Map.Entry<String, String> known : Store.getKnownApps(this).entrySet()) {
            if (!known.getKey().equals(getPackageName()) && seen.add(known.getKey())) {
                apps.add(new AppChoice(known.getValue(), known.getKey()));
            }
        }
        for (HistoryEntry entry : Store.getHistory(this)) {
            if (!entry.packageName.isEmpty() && seen.add(entry.packageName)) {
                apps.add(new AppChoice(entry.appLabel, entry.packageName));
            }
        }
        if (!rule.packageName.isEmpty() && seen.add(rule.packageName)) {
            apps.add(new AppChoice(rule.appLabel, rule.packageName));
        }
        apps.subList(1, apps.size()).sort(Comparator.comparing(choice -> choice.label.toLowerCase()));
    }

    private Rule findRule(String id) {
        if (id == null) return null;
        for (Rule candidate : Store.getRules(this)) if (candidate.id.equals(id)) return candidate;
        return null;
    }

    private int selectedApp() {
        for (int i = 0; i < apps.size(); i++) {
            if (apps.get(i).packageName.equals(rule.packageName)) return i;
        }
        return 0;
    }

    private TextView label(String text) {
        TextView label = Ui.text(this, text, 13);
        label.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 4));
        return label;
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        return spinner;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(value)) return i;
        return 0;
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static final class AppChoice {
        final String label;
        final String packageName;

        AppChoice(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class ConditionRow {
        final View root;
        final Spinner field;
        final Spinner operator;
        final EditText value;

        ConditionRow(View root, Spinner field, Spinner operator, EditText value) {
            this.root = root;
            this.field = field;
            this.operator = operator;
            this.value = value;
        }
    }
}
