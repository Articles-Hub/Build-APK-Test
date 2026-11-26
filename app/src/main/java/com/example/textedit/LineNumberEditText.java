package com.example.textedit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.TextUtils;
import android.util.AttributeSet;
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
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!showNumbers) return;
        Layout layout = getLayout();
        if (layout == null) return;
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
