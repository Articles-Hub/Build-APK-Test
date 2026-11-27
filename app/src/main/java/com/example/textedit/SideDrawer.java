package com.example.textedit;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * Ngăn kéo bên trái tự viết (không dùng thư viện AndroidX): vuốt từ mép trái hoặc bấm ☰ để mở,
 * chạm vào vùng tối hoặc vuốt sang trái để đóng. Con thứ nhất là nội dung, phần tử có id drawer_panel là ngăn kéo.
 */
public class SideDrawer extends FrameLayout {

    private View panel;
    private View scrim;
    private float progress; // 0 = đóng, 1 = mở
    private ValueAnimator anim;

    private final float edge;
    private final int slop;
    private float downX;
    private float downY;
    private boolean edgeCandidate;
    private boolean panelCandidate;
    private boolean scrimDown;
    private boolean dragging;
    private float startProgress;

    public SideDrawer(Context c) {
        super(c);
        edge = 22 * c.getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
    }

    public SideDrawer(Context c, AttributeSet a) {
        super(c, a);
        edge = 22 * c.getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        panel = findViewById(R.id.drawer_panel);
        scrim = new View(getContext());
        scrim.setBackgroundColor(0x99000000);
        scrim.setAlpha(0f);
        scrim.setVisibility(View.GONE);
        int idx = indexOfChild(panel);
        addView(scrim, idx, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        panel.setElevation(16 * getResources().getDisplayMetrics().density);
        apply(0f);
    }

    boolean isOpen() {
        return progress > 0.5f;
    }

    void open() {
        animateTo(1f);
    }

    void close() {
        animateTo(0f);
    }

    void toggle() {
        if (isOpen()) close();
        else open();
    }

    private void animateTo(final float target) {
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(progress, target);
        anim.setDuration(180);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                apply((Float) a.getAnimatedValue());
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                apply(target);
            }
        });
        anim.start();
    }

    private void apply(float p) {
        progress = Math.max(0f, Math.min(1f, p));
        int w = panel.getWidth() > 0 ? panel.getWidth() : panel.getLayoutParams().width;
        panel.setTranslationX(-(1f - progress) * w);
        panel.setVisibility(progress <= 0f ? View.INVISIBLE : View.VISIBLE);
        scrim.setAlpha(progress);
        scrim.setVisibility(progress <= 0f ? View.GONE : View.VISIBLE);
    }

    private int panelWidth() {
        return Math.max(1, panel.getWidth());
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (changed) apply(progress);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                dragging = false;
                scrimDown = false;
                edgeCandidate = progress <= 0f && downX <= edge;
                panelCandidate = progress > 0f && downX <= panelWidth();
                if (progress > 0f && downX > panelWidth()) {
                    scrimDown = true;
                    return true; // chạm vào vùng tối: chặn để đóng ngăn kéo
                }
                return false;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getX() - downX;
                float dy = e.getY() - downY;
                if (edgeCandidate && dx > slop && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                    dragging = true;
                    startProgress = 0f;
                    return true;
                }
                if (panelCandidate && dx < -slop && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                    dragging = true;
                    startProgress = 1f;
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (scrimDown && Math.abs(e.getX() - downX) > slop) {
                    scrimDown = false;
                    dragging = true;
                    startProgress = 1f;
                }
                if (dragging) {
                    if (anim != null) anim.cancel();
                    apply(startProgress + (e.getX() - downX) / panelWidth());
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    animateTo(progress > 0.5f ? 1f : 0f);
                } else if (scrimDown) {
                    close();
                }
                dragging = false;
                scrimDown = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (dragging) animateTo(progress > 0.5f ? 1f : 0f);
                dragging = false;
                scrimDown = false;
                return true;
            default:
                return true;
        }
    }
}
