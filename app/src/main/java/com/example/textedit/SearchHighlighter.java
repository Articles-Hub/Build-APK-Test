package com.example.textedit;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.style.BackgroundColorSpan;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tô nền mọi kết quả tìm kiếm trong vùng đang nhìn thấy (cộng thêm một màn hình ở mỗi phía). */
final class SearchHighlighter {

    private static final int MAX_MATCHES = 1500;

    private final LineNumberEditText ed;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ArrayList<Object> applied = new ArrayList<>();
    private Pattern pattern;
    private int color = 0x80FF9800; // cam

    private final Runnable task = new Runnable() {
        @Override
        public void run() {
            apply();
        }
    };

    SearchHighlighter(LineNumberEditText editor) {
        this.ed = editor;
    }

    void setColor(int argb) {
        color = argb;
    }

    void setPattern(Pattern p) {
        if (p == null) {
            clear();
            return;
        }
        pattern = p;
        schedule();
    }

    void onScrollChanged() {
        schedule();
    }

    void clear() {
        pattern = null;
        h.removeCallbacks(task);
        remove();
    }

    void schedule() {
        if (pattern == null) return;
        h.removeCallbacks(task);
        h.postDelayed(task, 120);
    }

    private void remove() {
        Editable e = ed.getText();
        for (Object o : applied) e.removeSpan(o);
        applied.clear();
    }

    private void apply() {
        remove();
        Pattern p = pattern;
        Layout lay = ed.getLayout();
        if (p == null || lay == null) return;
        Editable e = ed.getText();
        int len = e.length();
        if (len == 0) return;

        int height = Math.max(ed.getHeight(), 1);
        int sy = ed.getScrollY();
        int from = lay.getLineStart(lay.getLineForVertical(Math.max(0, sy - height)));
        int to = Math.min(len, lay.getLineEnd(lay.getLineForVertical(sy + 2 * height)));

        try {
            Matcher m = p.matcher(e);
            m.region(from, to);
            m.useTransparentBounds(true);
            m.useAnchoringBounds(false);
            int count = 0;
            while (count < MAX_MATCHES && m.find()) {
                if (m.end() > m.start()) {
                    BackgroundColorSpan span = new BackgroundColorSpan(color);
                    e.setSpan(span, m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    applied.add(span);
                    count++;
                }
            }
        } catch (RuntimeException ignored) {
            // mẫu regex quá nặng hoặc lỗi: bỏ qua việc tô sáng
        }
    }
}
