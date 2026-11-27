package com.example.textedit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.Layout;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.EditText;

/**
 * EditText có cột số dòng (đánh số theo dòng thật, không theo dòng bị cắt) và một chỉ mục dòng
 * dựng lười, dùng chung cho thanh trạng thái, nhảy tới dòng và vẽ số dòng — nên file hàng chục
 * nghìn dòng vẫn vẽ nhanh.
 */
public class LineNumberEditText extends EditText {

    interface Listener {
        void onScroll();

        void onSelection(int selStart, int selEnd);
    }

    private boolean showNumbers;
    private int digits = 2;
    private int gutterColor = 0xFFF2F2F2;
    private int basePadLeft;
    private Paint paint;
    private float density = 1f;
    private Listener listener;

    // Thanh cuộn nhanh (kéo nút bên mép phải để trượt hết file)
    private boolean fastScroll = true;
    private boolean draggingThumb;
    private float dragOffset;
    private long thumbUntil;
    private final RectF thumbRect = new RectF();
    private Paint bubblePaint;

    // Hiện ký tự ẩn (dấu cách, Tab)
    private boolean showWhitespace;
    private Paint wsPaint;

    private int[] starts = new int[1024];
    private int lineCount = 1;
    private boolean idxDirty = true;
    private char[] buf = new char[0];

    public LineNumberEditText(Context c) {
        super(c);
        init();
    }

    public LineNumberEditText(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    public LineNumberEditText(Context c, AttributeSet a, int style) {
        super(c, a, style);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        density = getResources().getDisplayMetrics().density;
        basePadLeft = getPaddingLeft();
        setSaveEnabled(false); // tránh nhét cả file lớn vào Bundle khi Activity bị dừng
        setVerticalScrollBarEnabled(false); // đã có thanh cuộn nhanh riêng
        bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wsPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    void setFastScrollEnabled(boolean on) {
        fastScroll = on;
        invalidate();
    }

    void setShowWhitespace(boolean on) {
        showWhitespace = on;
        invalidate();
    }

    void setListener(Listener l) {
        listener = l;
    }

    public void setShowLineNumbers(boolean show) {
        showNumbers = show;
        refreshGutter();
    }

    public void setGutterColor(int color) {
        gutterColor = color;
        invalidate();
    }

    /** Gọi lại sau khi đổi font hoặc cỡ chữ để tính lại độ rộng cột số dòng. */
    public void refreshGutter() {
        if (paint == null) return;
        int left = basePadLeft;
        if (showNumbers) {
            paint.setTextSize(getTextSize());
            paint.setTypeface(getTypeface());
            left += (int) (paint.measureText("0") * digits) + (int) (14 * density);
        }
        if (left != getPaddingLeft()) {
            setPadding(left, getPaddingTop(), getPaddingRight(), getPaddingBottom());
        }
        invalidate();
    }

    // ---------- chỉ mục dòng ----------

    private void ensureIndex() {
        if (!idxDirty) return;
        CharSequence t = getText();
        int n = t.length();
        if (buf.length < n) buf = new char[n + n / 4 + 16];
        TextUtils.getChars(t, 0, n, buf, 0);
        int count = 1;
        starts[0] = 0;
        for (int i = 0; i < n; i++) {
            if (buf[i] == '\n') {
                if (count >= starts.length) {
                    int[] bigger = new int[starts.length * 2];
                    System.arraycopy(starts, 0, bigger, 0, count);
                    starts = bigger;
                }
                starts[count++] = i + 1;
            }
        }
        lineCount = count;
        idxDirty = false;
    }

    /** Tổng số dòng thật (tính theo ký tự xuống dòng). */
    int totalLines() {
        ensureIndex();
        return lineCount;
    }

    /** Chỉ số dòng (từ 0) chứa vị trí ký tự offset. */
    int lineOfOffset(int offset) {
        ensureIndex();
        int lo = 0;
        int hi = lineCount - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (starts[mid] <= offset) lo = mid;
            else hi = mid - 1;
        }
        return lo;
    }

    /** Vị trí ký tự đầu của dòng (từ 0); kẹp vào khoảng hợp lệ. */
    int offsetOfLine(int line) {
        ensureIndex();
        return starts[Math.max(0, Math.min(line, lineCount - 1))];
    }

    // ---------- sự kiện ----------

    @Override
    protected void onTextChanged(CharSequence text, int start, int before, int after) {
        super.onTextChanged(text, start, before, after);
        idxDirty = true;
        if (showNumbers && paint != null) {
            int d = Math.max(2, String.valueOf(totalLines()).length());
            if (d != digits) {
                digits = d;
                post(new Runnable() {
                    @Override
                    public void run() {
                        refreshGutter();
                    }
                });
            }
        }
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (listener != null) listener.onSelection(selStart, selEnd);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (listener != null) listener.onScroll();
        if (t != oldt) showThumb();
    }

    // ---------- thanh cuộn nhanh ----------

    private int maxScroll() {
        Layout layout = getLayout();
        if (layout == null) return 0;
        return Math.max(0, layout.getHeight() + getCompoundPaddingTop() + getCompoundPaddingBottom() - getHeight());
    }

    /** Chỉ dùng thanh cuộn nhanh khi nội dung dài hơn khoảng hai màn hình. */
    private boolean thumbApplicable() {
        return fastScroll && getHeight() > 0 && maxScroll() > getHeight();
    }

    private void showThumb() {
        if (!thumbApplicable()) return;
        thumbUntil = SystemClock.uptimeMillis() + 1400;
        postInvalidateOnAnimation();
    }

    private float thumbHeight() {
        float h = getHeight();
        float content = h + maxScroll();
        return Math.max(48 * density, Math.min(h, h * h / Math.max(1f, content)));
    }

    private float thumbTop() {
        float track = getHeight() - thumbHeight();
        int max = maxScroll();
        return max <= 0 ? 0 : track * Math.min(1f, Math.max(0f, getScrollY() / (float) max));
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (thumbApplicable()) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    float top = thumbTop();
                    float h = thumbHeight();
                    boolean inZone = e.getX() >= getWidth() - 36 * density;
                    boolean nearThumb = e.getY() >= top - 16 * density && e.getY() <= top + h + 16 * density;
                    if (inZone && nearThumb) {
                        draggingThumb = true;
                        dragOffset = Math.max(0, Math.min(h, e.getY() - top));
                        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                        showThumb();
                        return true;
                    }
                    break;
                }
                case MotionEvent.ACTION_MOVE:
                    if (draggingThumb) {
                        float track = Math.max(1f, getHeight() - thumbHeight());
                        float frac = (e.getY() - dragOffset) / track;
                        frac = Math.max(0f, Math.min(1f, frac));
                        scrollTo(getScrollX(), (int) (frac * maxScroll()));
                        thumbUntil = SystemClock.uptimeMillis() + 1400;
                        invalidate();
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (draggingThumb) {
                        draggingThumb = false;
                        showThumb();
                        return true;
                    }
                    break;
                default:
                    break;
            }
        }
        return super.onTouchEvent(e);
    }

    private void drawThumb(Canvas canvas) {
        if (!thumbApplicable()) return;
        long now = SystemClock.uptimeMillis();
        long left = draggingThumb ? 1000 : thumbUntil - now;
        if (left <= 0) return;
        float alpha = Math.min(1f, left / 300f);

        int sx = getScrollX();
        int sy = getScrollY();
        float w = (draggingThumb ? 10 : 5) * density;
        float right = sx + getWidth() - 2 * density;
        float top = sy + thumbTop();
        thumbRect.set(right - w, top, right, top + thumbHeight());
        int base = getCurrentTextColor() & 0x00FFFFFF;
        bubblePaint.setStyle(Paint.Style.FILL);
        bubblePaint.setColor(((int) (alpha * (draggingThumb ? 190 : 120)) << 24) | base);
        canvas.drawRoundRect(thumbRect, w / 2, w / 2, bubblePaint);

        if (draggingThumb) {
            Layout layout = getLayout();
            if (layout != null) {
                int line = lineOfOffset(layout.getLineStart(layout.getLineForVertical(getScrollY()))) + 1;
                String label = line + " / " + totalLines();
                bubblePaint.setTextSize(14 * density);
                bubblePaint.setTypeface(android.graphics.Typeface.DEFAULT);
                float tw = bubblePaint.measureText(label);
                float padH = 10 * density;
                float bh = 30 * density;
                float bx2 = thumbRect.left - 10 * density;
                RectF bubble = new RectF(bx2 - tw - 2 * padH, thumbRect.centerY() - bh / 2, bx2,
                        thumbRect.centerY() + bh / 2);
                bubblePaint.setColor(0xE6303030);
                canvas.drawRoundRect(bubble, 8 * density, 8 * density, bubblePaint);
                bubblePaint.setColor(0xFFFFFFFF);
                bubblePaint.setTextAlign(Paint.Align.LEFT);
                canvas.drawText(label, bubble.left + padH, bubble.centerY() + 5 * density, bubblePaint);
            }
        }
        postInvalidateOnAnimation();
    }

    // ---------- hiện ký tự ẩn ----------

    private void drawWhitespace(Canvas canvas, Layout layout, CharSequence text, int first, int last) {
        wsPaint.setTextSize(getTextSize());
        wsPaint.setTypeface(getTypeface());
        wsPaint.setColor((getCurrentTextColor() & 0x00FFFFFF) | 0x55000000);
        wsPaint.setTextAlign(Paint.Align.CENTER);
        float spaceW = wsPaint.measureText(" ");
        int offsetX = getCompoundPaddingLeft();
        int offsetY = getExtendedPaddingTop();
        int budget = 4000;
        for (int line = first; line <= last && budget > 0; line++) {
            int s = layout.getLineStart(line);
            int e = Math.min(layout.getLineEnd(line), s + 600);
            float baseline = layout.getLineBaseline(line) + offsetY;
            int i = s;
            while (i < e && budget > 0) {
                char c = text.charAt(i);
                if (c == ' ') {
                    int runEnd = i;
                    while (runEnd < e && text.charAt(runEnd) == ' ') runEnd++;
                    float x0 = offsetX + layout.getPrimaryHorizontal(i);
                    for (int k = 0; k < runEnd - i && budget > 0; k++, budget--) {
                        canvas.drawText("·", x0 + (k + 0.5f) * spaceW, baseline, wsPaint);
                    }
                    i = runEnd;
                } else if (c == '\t') {
                    float x0 = offsetX + layout.getPrimaryHorizontal(i);
                    float x1 = offsetX + layout.getPrimaryHorizontal(i + 1);
                    canvas.drawText("→", (x0 + x1) / 2f, baseline, wsPaint);
                    budget--;
                    i++;
                } else {
                    i++;
                }
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Layout layout = getLayout();
        if (layout == null) return;
        if (showWhitespace && getText().length() > 0) {
            try {
                int f = layout.getLineForVertical(getScrollY());
                int l = layout.getLineForVertical(getScrollY() + getHeight());
                drawWhitespace(canvas, layout, getText(), f, l);
            } catch (RuntimeException ex) {
                showWhitespace = false; // không vẽ được thì tự tắt, tránh làm sập ứng dụng
            }
        }
        if (showNumbers) drawGutter(canvas, layout);
        drawThumb(canvas);
    }

    private void drawGutter(Canvas canvas, Layout layout) {
        CharSequence text = getText();

        int sx = getScrollX();
        int sy = getScrollY();
        int gutterWidth = getPaddingLeft();

        // Nền cột số dòng (đặc, để chữ cuộn ngang không chồng lên số).
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(gutterColor);
        canvas.drawRect(sx, sy, sx + gutterWidth, sy + getHeight(), paint);

        paint.setTextSize(getTextSize());
        paint.setTypeface(getTypeface());
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setColor((getCurrentTextColor() & 0x00FFFFFF) | 0x99000000);

        int first = layout.getLineForVertical(sy);
        int last = layout.getLineForVertical(sy + getHeight());

        float x = sx + gutterWidth - 8 * density;
        int offsetY = getExtendedPaddingTop();
        for (int i = first; i <= last; i++) {
            int s = layout.getLineStart(i);
            boolean startsLogicalLine = s == 0 || text.charAt(s - 1) == '\n';
            if (startsLogicalLine) {
                canvas.drawText(String.valueOf(lineOfOffset(s) + 1), x,
                        layout.getLineBaseline(i) + offsetY, paint);
            }
        }
    }
}
