package com.example.textedit;

/** Thống kê văn bản: từ, dòng, ký tự. */
final class TextStats {
    final int words;
    final int lines;
    final int chars;       // số ký tự (điểm mã Unicode)
    final int charsNoSpace;

    private TextStats(int words, int lines, int chars, int charsNoSpace) {
        this.words = words;
        this.lines = lines;
        this.chars = chars;
        this.charsNoSpace = charsNoSpace;
    }

    static TextStats of(CharSequence t) {
        int n = t.length();
        int words = 0;
        int lines = n == 0 ? 0 : 1;
        int noSpace = 0;
        boolean inWord = false;
        for (int i = 0; i < n; i++) {
            char c = t.charAt(i);
            if (c == '\n') lines++;
            boolean ws = Character.isWhitespace(c) || c == ' ';
            if (!ws) {
                if (!Character.isLowSurrogate(c)) noSpace++;
                if (!inWord) words++;
            }
            inWord = !ws;
        }
        // Một dòng trống cuối cùng (văn bản kết thúc bằng '\n') không phải một dòng nữa.
        if (n > 0 && t.charAt(n - 1) == '\n' && lines > 1) lines--;
        int chars = Character.codePointCount(t, 0, n);
        return new TextStats(words, lines, chars, noSpace);
    }
}
