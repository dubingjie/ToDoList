package com.example.todolist;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

/** Three adjacent months move together, keeping date taps and vertical scrolling intact. */
final class CalendarPager extends ViewGroup {
    interface Listener { void changed(int delta); }
    private final Listener listener;
    private final int slop, minVelocity, maxVelocity;
    private float downX, downY, offset;
    private boolean dragging, vertical, settling;
    private VelocityTracker velocity;
    private ValueAnimator animator;

    CalendarPager(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        ViewConfiguration config = ViewConfiguration.get(context);
        slop = config.getScaledTouchSlop();
        minVelocity = config.getScaledMinimumFlingVelocity();
        maxVelocity = config.getScaledMaximumFlingVelocity();
        setClipChildren(true);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(width, height);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).layout(0, 0, getWidth(), getHeight());
        }
        move(offset);
    }

    private void move(float value) {
        offset = Math.max(-getWidth(), Math.min(getWidth(), value));
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).setTranslationX((i - 1) * getWidth() + offset);
        }
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (settling) return true;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getX(); downY = event.getY();
            dragging = false; vertical = false;
            velocity = VelocityTracker.obtain();
            // Delay parent interception just until the gesture direction is known.
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        if (velocity != null) velocity.addMovement(event);
        if (action == MotionEvent.ACTION_MOVE && !dragging && !vertical) {
            float dx = Math.abs(event.getX() - downX), dy = Math.abs(event.getY() - downY);
            if (dx > slop && dx > dy) dragging = true;
            else if (dy > slop) {
                vertical = true;
                getParent().requestDisallowInterceptTouchEvent(false);
            }
        }
        boolean handled = super.dispatchTouchEvent(event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            getParent().requestDisallowInterceptTouchEvent(false);
            if (velocity != null) { velocity.recycle(); velocity = null; }
        }
        return handled;
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) { return dragging; }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && dragging) {
            move(event.getX() - downX);
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            float speed = 0;
            if (velocity != null) { velocity.computeCurrentVelocity(1000, maxVelocity); speed = velocity.getXVelocity(); }
            int delta = 0;
            if (dragging && getWidth() > 0) {
                move(event.getX() - downX);
                if (Math.abs(speed) > minVelocity * 4 && Math.abs(offset) > slop) delta = speed < 0 ? 1 : -1;
                else if (Math.abs(offset) > getWidth() * .25f) delta = offset < 0 ? 1 : -1;
            }
            settle(delta);
        } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            settle(0);
        }
        return true;
    }

    private void settle(int delta) {
        dragging = false;
        float target = -delta * getWidth();
        if (offset == target && delta == 0) return;
        settling = true;
        animator = ValueAnimator.ofFloat(offset, target);
        animator.setDuration(180 + (long) (100 * Math.abs(target - offset) / Math.max(1, getWidth())));
        animator.setInterpolator(new DecelerateInterpolator(1.5f));
        animator.addUpdateListener(value -> move((float) value.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                settling = false;
                if (!cancelled && delta != 0) listener.changed(delta);
            }
        });
        animator.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        if (velocity != null) { velocity.recycle(); velocity = null; }
        super.onDetachedFromWindow();
    }
}
