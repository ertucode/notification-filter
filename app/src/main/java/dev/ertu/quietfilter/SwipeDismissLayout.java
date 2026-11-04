package dev.ertu.quietfilter;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

public final class SwipeDismissLayout extends LinearLayout {
    private float downX;
    private boolean swiping;
    private boolean childCancelled;
    private final int touchSlop;
    private Runnable onDismiss;

    public SwipeDismissLayout(Context context, AttributeSet attributes) {
        super(context, attributes);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClickable(true);
    }

    void setOnSwipeDismissListener(Runnable listener) {
        onDismiss = listener;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        float distance = event.getRawX() - downX;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = event.getRawX();
            swiping = false;
            childCancelled = false;
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            if (Math.abs(distance) > touchSlop) swiping = true;
            if (swiping) {
                if (!childCancelled) {
                    MotionEvent cancel = MotionEvent.obtain(event);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancel);
                    cancel.recycle();
                    childCancelled = true;
                }
                setTranslationX(distance);
                setAlpha(Math.max(0.35f, 1f - Math.abs(distance) / getWidth()));
                return true;
            }
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP && swiping) {
            if (Math.abs(distance) >= getWidth() * 0.28f && onDismiss != null) {
                onDismiss.run();
            } else {
                animate().translationX(0).alpha(1f).setDuration(140).start();
            }
            return true;
        } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            animate().translationX(0).alpha(1f).setDuration(140).start();
        }
        return super.dispatchTouchEvent(event) || isClickable();
    }
}
