package com.example.textedit;

/**
 * Kiểu xuống dòng. Trong ô soạn thảo luôn dùng "\n"; khi đọc file thì chuẩn hóa về "\n" và nhớ kiểu gốc,
 * khi lưu thì đổi lại theo kiểu đã nhớ (hoặc kiểu người dùng chọn).
 */
final class Eol {
    static final String LF = "\n";
    static final String CRLF = "\r\n";
    static final String CR = "\r";

    private Eol() { }

    /** Kiểu chiếm đa số trong văn bản; null nếu văn bản không có ký tự xuống dòng nào. */
    static String detect(String text) {
        int lf = 0;
        int crlf = 0;
        int cr = 0;
        int n = text.length();
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (c == '\r') {
                if (i + 1 < n && text.charAt(i + 1) == '\n') {
                    crlf++;
                    i++;
                } else {
                    cr++;
                }
            } else if (c == '\n') {
                lf++;
            }
        }
        if (lf == 0 && crlf == 0 && cr == 0) return null;
        if (crlf > lf && crlf >= cr) return CRLF;
        if (cr > lf && cr > crlf) return CR;
        return LF;
    }

    /** Đổi mọi kiểu xuống dòng về "\n". Trả về chính chuỗi đó nếu không có "\r". */
    static String normalize(String text) {
        if (text.indexOf('\r') < 0) return text;
        StringBuilder sb = new StringBuilder(text.length());
        int n = text.length();
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (c == '\r') {
                sb.append('\n');
                if (i + 1 < n && text.charAt(i + 1) == '\n') i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Đổi "\n" thành kiểu xuống dòng đã chọn khi lưu. */
    static String apply(String text, String eol) {
        if (eol == null || eol.equals(LF)) return text;
        return text.replace("\n", eol);
    }

    static String fromKey(String key) {
        if ("crlf".equals(key)) return CRLF;
        if ("cr".equals(key)) return CR;
        return LF;
    }

    static String label(String eol) {
        if (CRLF.equals(eol)) return "CRLF";
        if (CR.equals(eol)) return "CR";
        return "LF";
    }
}
