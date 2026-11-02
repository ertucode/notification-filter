package dev.ertu.quietfilter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    private static final int GREEN = Color.rgb(49, 92, 73);
    private static final int DARK_GREEN = Color.rgb(35, 67, 53);
    private static final int PALE_GREEN = Color.rgb(229, 237, 233);
    private static final int DISABLED = Color.rgb(205, 209, 206);

    private Ui() {}

    static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    static TextView text(Context context, String value, float size) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(35, 40, 37));
        return view;
    }

    static TextView heading(Context context, String value) {
        TextView view = text(context, value, 20);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    static View divider(Context context) {
        View line = new View(context);
        line.setBackgroundColor(Color.rgb(225, 227, 223));
        line.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 1)));
        return line;
    }

    static void stylePrimary(Button button) {
        styleButton(button, Color.WHITE, GREEN, DARK_GREEN);
    }

    static void styleSecondary(Button button) {
        styleButton(button, DARK_GREEN, PALE_GREEN, Color.rgb(211, 224, 217));
    }

    static void styleDanger(Button button) {
        styleButton(button, Color.rgb(130, 35, 35), Color.rgb(250, 230, 230),
                Color.rgb(241, 210, 210));
    }

    static void styleTab(Button button, boolean selected) {
        if (selected) stylePrimary(button); else styleSecondary(button);
    }

    private static void styleButton(Button button, int textColor, int normalColor, int pressedColor) {
        int[][] states = new int[][]{
                new int[]{-android.R.attr.state_enabled},
                new int[]{android.R.attr.state_pressed},
                new int[]{}
        };
        button.setBackgroundTintList(new ColorStateList(states,
                new int[]{DISABLED, pressedColor, normalColor}));
        button.setTextColor(new ColorStateList(states,
                new int[]{Color.rgb(120, 124, 121), textColor, textColor}));
        button.setAllCaps(false);
        button.setMinHeight(dp(button.getContext(), 48));
    }

    @SuppressWarnings("deprecation")
    static void respectSystemBars(View root) {
        final int left = root.getPaddingLeft();
        final int top = root.getPaddingTop();
        final int right = root.getPaddingRight();
        final int bottom = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(
                    left + insets.getSystemWindowInsetLeft(),
                    top + insets.getSystemWindowInsetTop(),
                    right + insets.getSystemWindowInsetRight(),
                    bottom + insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();
    }
}
