package com.example.textedit;

/**
 * Quy tắc tự đóng cặp ngoặc/nháy: gõ ( [ { " ' ` thì chèn luôn ký tự đóng, gõ ký tự đóng ngay trước
 * ký tự đóng đã có thì chỉ nhảy qua, xóa lùi ký tự mở đứng ngay trước ký tự đóng thì xóa cả cặp.
 */
final class AutoPair {

    static final int NONE = 0;
    /** Chèn ký tự mở + ký tự đóng, con trỏ nằm giữa. */
    static final int PAIR = 1;
    /** Ký tự đóng đã có sẵn ngay sau con trỏ: không chèn, chỉ nhảy qua. */
    static final int SKIP = 2;

    private AutoPair() { }

    static char closer(char open) {
        switch (open) {
            case '(':
                return ')';
            case '[':
                return ']';
            case '{':
                return '}';
            default:
                return open; // " ' ` tự đóng bằng chính nó
        }
    }

    static boolean isBracketOpen(char c) {
        return c == '(' || c == '[' || c == '{';
    }

    static boolean isQuote(char c) {
        return c == '"' || c == '\'' || c == '`';
    }

    private static boolean wordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * Quyết định khi gõ ký tự c thay cho đoạn [dstart, dend) của dest.
     *
     * @param quotes có tự đóng cặp dấu nháy hay không (chỉ nên bật với file mã nguồn)
     */
    static int onType(CharSequence dest, int dstart, int dend, char c, boolean quotes) {
        boolean selection = dend > dstart;
        char next = dend < dest.length() ? dest.charAt(dend) : '\0';
        char prev = dstart > 0 ? dest.charAt(dstart - 1) : '\0';

        if (!selection && (c == ')' || c == ']' || c == '}') && next == c) return SKIP;
        if (selection) return NONE;

        if (isBracketOpen(c)) {
            return wordChar(next) ? NONE : PAIR;
        }
        if (quotes && isQuote(c)) {
            if (next == c) return SKIP;
            if (wordChar(prev) || prev == c) return NONE; // don't, hoặc gõ nháy thứ ba
            if (wordChar(next)) return NONE;
            return PAIR;
        }
        return NONE;
    }

    /** Xóa lùi một ký tự ở [dstart, dstart+1): có phải đang xóa ký tự mở của một cặp rỗng không. */
    static boolean deletesEmptyPair(CharSequence dest, int dstart, int dend) {
        if (dend - dstart != 1 || dend >= dest.length()) return false;
        char open = dest.charAt(dstart);
        if (!isBracketOpen(open) && !isQuote(open)) return false;
        return dest.charAt(dend) == closer(open);
    }
}
