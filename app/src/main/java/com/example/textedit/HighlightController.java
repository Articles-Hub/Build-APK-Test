package com.example.textedit;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tô màu cú pháp mà không làm chậm file lớn: phân tích token ở luồng nền (có debounce),
 * nhưng chỉ gắn span cho vùng đang hiển thị (cộng thêm một màn hình trên và dưới).
 */
final class HighlightController {

    private final LineNumberEditText ed;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ExecutorService ex = Executors.newSingleThreadExecutor();
    private final ArrayList<Object> applied = new ArrayList<>();

    private Lang lang;
    private boolean enabled = true;
    private int[] palette = palette(false);
    private int version;
    private Lexer.Tokens toks;

    private final Runnable computeTask = new Runnable() {
        @Override
        public void run() {
            startCompute();
        }
    };
    private final Runnable applyTask = new Runnable() {
        @Override
        public void run() {
            applyWindow();
        }
    };

    HighlightController(LineNumberEditText editor) {
        this.ed = editor;
        ed.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void afterTextChanged(Editable s) {
                version++;
                if (enabled && lang != null) {
                    h.removeCallbacks(computeTask);
                    h.postDelayed(computeTask, 350);
                }
            }
        });
    }

    static int[] palette(boolean dark) {
        int[] p = new int[12];
        if (dark) {
            p[Lexer.KW] = 0xFFCC7832;
            p[Lexer.STR] = 0xFF6A8759;
            p[Lexer.COM] = 0xFF808080;
            p[Lexer.NUM] = 0xFF6897BB;
            p[Lexer.TAG] = 0xFFE8BF6A;
            p[Lexer.ATTR] = 0xFF9876AA;
            p[Lexer.META] = 0xFFBBB529;
            p[Lexer.HEAD] = 0xFFFFC66D;
            p[Lexer.TYPE] = 0xFF20999D;
            p[Lexer.ADD] = 0xFF5FB760;
            p[Lexer.DEL] = 0xFFFF6B68;
        } else {
            p[Lexer.KW] = 0xFF0033B3;
            p[Lexer.STR] = 0xFF067D17;
            p[Lexer.COM] = 0xFF7F7F7F;
            p[Lexer.NUM] = 0xFF1750EB;
            p[Lexer.TAG] = 0xFF871094;
            p[Lexer.ATTR] = 0xFF7A5C00;
            p[Lexer.META] = 0xFF9E880D;
            p[Lexer.HEAD] = 0xFFB8004A;
            p[Lexer.TYPE] = 0xFF00627A;
            p[Lexer.ADD] = 0xFF1B7F1B;
            p[Lexer.DEL] = 0xFFC62828;
        }
        return p;
    }

    /** Gọi khi đổi tài liệu (Editable mới) hoặc đổi ngôn ngữ/cài đặt. */
    void configure(Lang l, boolean on, boolean dark) {
        h.removeCallbacks(computeTask);
        h.removeCallbacks(applyTask);
        applied.clear(); // Editable cũ đã bị thay nên không cần gỡ span
        toks = null;
        lang = l;
        enabled = on;
        palette = palette(dark);
        version++;
        if (enabled && lang != null) {
            h.postDelayed(computeTask, 60);
        } else {
            removeApplied();
        }
    }

    void onScroll() {
        if (toks == null) return;
        h.removeCallbacks(applyTask);
        h.postDelayed(applyTask, 50);
    }

    private void startCompute() {
        if (!enabled || lang == null) return;
        final int v = version;
        final Lang l = lang;
        final String snapshot = ed.getText().toString();
        ex.execute(new Runnable() {
            @Override
            public void run() {
                final Lexer.Tokens t;
                try {
                    t = Lexer.lex(snapshot, l);
                } catch (Throwable e) {
                    return;
                }
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        if (v != version || l != lang || !enabled) return;
                        toks = t;
                        applyWindow();
                    }
                });
            }
        });
    }

    private void removeApplied() {
        Editable e = ed.getText();
        for (Object o : applied) e.removeSpan(o);
        applied.clear();
    }

    private void applyWindow() {
        Layout lay = ed.getLayout();
        Lexer.Tokens t = toks;
        if (lay == null || t == null || !enabled) return;
        Editable e = ed.getText();
        int len = e.length();
        if (len == 0) return;

        int height = Math.max(ed.getHeight(), 1);
        int sy = ed.getScrollY();
        int top = Math.max(0, sy - height);
        int bottom = sy + 2 * height;
        int firstLine = lay.getLineForVertical(top);
        int lastLine = lay.getLineForVertical(bottom);
        int from = lay.getLineStart(firstLine);
        int to = Math.min(len, lay.getLineEnd(lastLine));

        removeApplied();

        int n = t.count();
        int lo = 0;
        int hi = n;
        while (lo < hi) { // token đầu tiên có điểm kết thúc > from
            int mid = (lo + hi) >>> 1;
            if (t.a[mid * 3 + 1] > from) hi = mid;
            else lo = mid + 1;
        }
        for (int k = lo; k < n; k++) {
            int s = t.a[k * 3];
            int en = t.a[k * 3 + 1];
            if (s >= to) break;
            if (en > len) break;
            int color = palette[t.a[k * 3 + 2]];
            if (color == 0) continue;
            ForegroundColorSpan span = new ForegroundColorSpan(color);
            e.setSpan(span, s, en, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            applied.add(span);
        }
    }

    /** Màu cho các thành phần khác (ví dụ cột số dòng) dựa theo giao diện. */
    static int dim(int color) {
        return (color & 0x00FFFFFF) | 0x99000000;
    }

    static boolean isLight(int c) {
        double lum = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0;
        return lum > 0.5;
    }
}
