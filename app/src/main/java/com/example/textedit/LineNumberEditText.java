package com.example.textedit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.util.AttributeSet;
import android.widget.EditText;

/** EditText có thể hiện số dòng (đánh số theo dòng thật, không theo dòng bị cắt). */
public class LineNumberEditText extends EditText {

    private boolean showNumbers;
    private int digits = 2;
    private int gutterColor = 0xFFF2F2F2;
    private int basePadLeft;
    private Paint paint;
    private float density = 1f;

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

    @Override
    protected void onTextChanged(CharSequence text, int start, int before, int after) {
        super.onTextChanged(text, start, before, after);
        if (!showNumbers || paint == null) return;
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') lines++;
        }
        int d = Math.max(2, String.valueOf(lines).length());
        if (d != digits) {
            digits = d;
            refreshGutter();
        }
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

        // Số dòng thật của dòng hiển thị đầu tiên.
        int number = 1;
        int firstStart = layout.getLineStart(first);
        for (int i = 0; i < firstStart; i++) {
            if (text.charAt(i) == '\n') number++;
        }

        float x = sx + gutterWidth - 8 * density;
        int offsetY = getExtendedPaddingTop();
        for (int i = first; i <= last; i++) {
            int s = layout.getLineStart(i);
            boolean startsLogicalLine = s == 0 || text.charAt(s - 1) == '\n';
            if (i > first && startsLogicalLine) number++;
            if (startsLogicalLine) {
                canvas.drawText(String.valueOf(number), x, layout.getLineBaseline(i) + offsetY, paint);
            }
        }
    }
}
