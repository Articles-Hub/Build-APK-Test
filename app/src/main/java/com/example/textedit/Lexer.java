package com.example.textedit;

import java.util.Locale;

/**
 * Bộ phân tách token đơn giản (không dùng regex) để tô màu cú pháp.
 * Kết quả là các bộ ba (bắt đầu, kết thúc, lớp token) theo thứ tự tăng dần.
 */
final class Lexer {

    static final int KW = 1, STR = 2, COM = 3, NUM = 4, TAG = 5, ATTR = 6, META = 7,
            HEAD = 8, TYPE = 9, ADD = 10, DEL = 11;

    static final class Tokens {
        int[] a = new int[256];
        int n = 0;

        void add(int s, int e, int cls) {
            if (e <= s) return;
            if (n + 3 > a.length) {
                int[] b = new int[a.length * 2];
                System.arraycopy(a, 0, b, 0, n);
                a = b;
            }
            a[n++] = s;
            a[n++] = e;
            a[n++] = cls;
        }

        int count() {
            return n / 3;
        }
    }

    private Lexer() { }

    static Tokens lex(String s, Lang L) {
        Tokens t = new Tokens();
        if (L == null) return t;
        switch (L.kind) {
            case Lang.HTML:
                html(s, L, t);
                break;
            case Lang.CSS:
                css(s, 0, s.length(), L, t);
                break;
            case Lang.MARKDOWN:
                markdown(s, L, t);
                break;
            case Lang.DIFF:
                diff(s, t);
                break;
            case Lang.INI:
                ini(s, t);
                break;
            case Lang.YAML:
                yaml(s, t);
                break;
            case Lang.LOG:
                log(s, t);
                break;
            default:
                generic(s, 0, s.length(), L, t);
        }
        return t;
    }

    // ---------- chung ----------

    private static boolean starts(String s, int i, int to, String p) {
        return p != null && i + p.length() <= to && s.startsWith(p, i);
    }

    private static int eol(String s, int i, int to) {
        int e = s.indexOf('\n', i);
        return (e < 0 || e > to) ? to : e;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static int scanString(String s, int i, int to, char q, boolean multi) {
        int j = i + 1;
        while (j < to) {
            char ch = s.charAt(j);
            if (ch == '\\') {
                j += 2;
                continue;
            }
            if (ch == q) {
                j++;
                break;
            }
            if (!multi && ch == '\n') break;
            j++;
        }
        return Math.min(j, to);
    }

    static void generic(String s, int from, int to, Lang L, Tokens t) {
        int i = from;
        while (i < to) {
            char c = s.charAt(i);
            if (c <= ' ') {
                i++;
                continue;
            }

            if (L.blockS != null && starts(s, i, to, L.blockS)) {
                int e = s.indexOf(L.blockE, i + L.blockS.length());
                e = (e < 0 || e + L.blockE.length() > to) ? to : e + L.blockE.length();
                t.add(i, e, Lexer.COM);
                i = e;
                continue;
            }

            boolean done = false;
            for (String p : L.lineC) {
                if (starts(s, i, to, p)) {
                    int e = eol(s, i, to);
                    t.add(i, e, Lexer.COM);
                    i = e;
                    done = true;
                    break;
                }
            }
            if (done) continue;

            if (L.quotes.indexOf(c) >= 0) {
                int e;
                if (L.triple && i + 2 < to && s.charAt(i + 1) == c && s.charAt(i + 2) == c) {
                    String q3 = new String(new char[]{c, c, c});
                    int k = s.indexOf(q3, i + 3);
                    e = (k < 0 || k + 3 > to) ? to : k + 3;
                } else {
                    e = scanString(s, i, to, c, c == '`');
                }
                int cls = Lexer.STR;
                if (L.keyStr) {
                    int k = e;
                    while (k < to && (s.charAt(k) == ' ' || s.charAt(k) == '\t')) k++;
                    if (k < to && s.charAt(k) == ':') cls = Lexer.ATTR;
                }
                t.add(i, e, cls);
                i = e;
                continue;
            }

            if (c == '\'' && L.charLit) {
                if (i + 2 < to && s.charAt(i + 1) != '\\' && s.charAt(i + 2) == '\'') {
                    t.add(i, i + 3, Lexer.STR);
                    i += 3;
                } else if (i + 3 < to && s.charAt(i + 1) == '\\' && s.charAt(i + 3) == '\'') {
                    t.add(i, i + 4, Lexer.STR);
                    i += 4;
                } else {
                    i++; // nhãn kiểu 'a
                }
                continue;
            }

            if (isDigit(c) || (c == '.' && i + 1 < to && isDigit(s.charAt(i + 1)))) {
                int j = i + 1;
                while (j < to) {
                    char ch = s.charAt(j);
                    if (Character.isLetterOrDigit(ch) || ch == '_') j++;
                    else if (ch == '.' && j + 1 < to && isDigit(s.charAt(j + 1))) j++;
                    else break;
                }
                t.add(i, j, Lexer.NUM);
                i = j;
                continue;
            }

            if (c == '#' && L.preproc) {
                int j = i + 1;
                while (j < to && (s.charAt(j) == ' ' || s.charAt(j) == '\t')) j++;
                while (j < to && Character.isLetter(s.charAt(j))) j++;
                t.add(i, j, Lexer.META);
                i = j;
                continue;
            }

            if (c == '@' && i + 1 < to && Character.isLetter(s.charAt(i + 1))) {
                int j = i + 1;
                while (j < to && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_' || s.charAt(j) == '.')) j++;
                t.add(i, j, L.atAttr ? Lexer.ATTR : Lexer.META);
                i = j;
                continue;
            }

            if (c == '$' && L.dollar) {
                int j = i + 1;
                if (j < to && s.charAt(j) == '{') {
                    int k = s.indexOf('}', j);
                    j = (k < 0 || k >= to) ? to : k + 1;
                } else if (j < to && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_')) {
                    while (j < to && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_')) j++;
                } else if (j < to && "@?#*!$".indexOf(s.charAt(j)) >= 0) {
                    j++;
                }
                t.add(i, j, Lexer.ATTR);
                i = j;
                continue;
            }

            if (c == '\\' && L.backslashKw) {
                int j = i + 1;
                if (j < to && Character.isLetter(s.charAt(j))) {
                    while (j < to && Character.isLetter(s.charAt(j))) j++;
                } else if (j < to) {
                    j++;
                }
                t.add(i, j, Lexer.KW);
                i = j;
                continue;
            }

            if (Character.isLetter(c) || c == '_' || (c == '$' && !L.dollar)) {
                int j = i + 1;
                while (j < to) {
                    char ch = s.charAt(j);
                    if (Character.isLetterOrDigit(ch) || ch == '_' || (ch == '$' && !L.dollar)
                            || (L.dashIdent && ch == '-' && j + 1 < to && Character.isLetter(s.charAt(j + 1)))) {
                        j++;
                    } else {
                        break;
                    }
                }
                int len = j - i;
                if (len <= 24) {
                    String w = s.substring(i, j);
                    if (L.ci) w = w.toLowerCase(Locale.ROOT);
                    if (L.kw.contains(w)) {
                        t.add(i, j, Lexer.KW);
                    } else if (L.types && Character.isUpperCase(c) && len > 1 && hasLower(s, i, j)) {
                        t.add(i, j, Lexer.TYPE);
                    }
                } else if (L.types && Character.isUpperCase(c) && hasLower(s, i, j)) {
                    t.add(i, j, Lexer.TYPE);
                }
                i = j;
                continue;
            }

            i++;
        }
    }

    private static boolean hasLower(String s, int from, int to) {
        for (int k = from; k < to; k++) {
            if (Character.isLowerCase(s.charAt(k))) return true;
        }
        return false;
    }

    // ---------- HTML / XML ----------

    private static boolean tagChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ':' || c == '.';
    }

    private static boolean attrChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ':' || c == '.' || c == '@';
    }

    static int indexOfIgnoreCase(String s, String q, int from) {
        int last = s.length() - q.length();
        for (int i = from; i <= last; i++) {
            if (s.regionMatches(true, i, q, 0, q.length())) return i;
        }
        return -1;
    }

    static void html(String s, Lang L, Tokens t) {
        int n = s.length();
        int i = 0;
        while (i < n) {
            int lt = s.indexOf('<', i);
            if (lt < 0) break;
            if (s.startsWith("<!--", lt)) {
                int e = s.indexOf("-->", lt + 4);
                e = e < 0 ? n : e + 3;
                t.add(lt, e, Lexer.COM);
                i = e;
                continue;
            }
            if (s.startsWith("<![CDATA[", lt)) {
                int e = s.indexOf("]]>", lt + 9);
                e = e < 0 ? n : e + 3;
                t.add(lt, e, Lexer.STR);
                i = e;
                continue;
            }
            if (s.startsWith("<!", lt) || s.startsWith("<?", lt)) {
                int e = s.indexOf('>', lt);
                e = e < 0 ? n : e + 1;
                t.add(lt, e, Lexer.META);
                i = e;
                continue;
            }
            int p = lt + 1;
            boolean close = false;
            if (p < n && s.charAt(p) == '/') {
                close = true;
                p++;
            }
            if (p >= n || !Character.isLetter(s.charAt(p))) {
                i = lt + 1;
                continue;
            }
            int q = p;
            while (q < n && tagChar(s.charAt(q))) q++;
            String name = s.substring(p, q).toLowerCase(Locale.ROOT);
            t.add(lt, q, Lexer.TAG);

            int k = q;
            while (k < n) {
                char ch = s.charAt(k);
                if (ch == '>') {
                    t.add(k, k + 1, Lexer.TAG);
                    k++;
                    break;
                }
                if (ch == '/' && k + 1 < n && s.charAt(k + 1) == '>') {
                    t.add(k, k + 2, Lexer.TAG);
                    k += 2;
                    break;
                }
                if (ch == '"' || ch == '\'') {
                    int e = s.indexOf(ch, k + 1);
                    e = e < 0 ? n : e + 1;
                    t.add(k, e, Lexer.STR);
                    k = e;
                    continue;
                }
                if (Character.isLetter(ch) || ch == '_' || ch == ':' || ch == '@') {
                    int e = k;
                    while (e < n && attrChar(s.charAt(e))) e++;
                    t.add(k, e, Lexer.ATTR);
                    k = e;
                    continue;
                }
                k++;
            }
            i = k;

            if (!close && !L.xml && (name.equals("script") || name.equals("style"))) {
                int end = indexOfIgnoreCase(s, "</" + name, i);
                if (end < 0) end = n;
                if (name.equals("script")) {
                    Lang js = Langs.byName("JavaScript");
                    if (js != null) generic(s, i, end, js, t);
                } else {
                    Lang cs = Langs.byName("CSS");
                    if (cs != null) css(s, i, end, cs, t);
                }
                i = end;
            }
        }
    }

    // ---------- CSS / SCSS / Less ----------

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    static void css(String s, int from, int to, Lang L, Tokens t) {
        int i = from;
        int depth = 0;
        int paren = 0;
        while (i < to) {
            char c = s.charAt(i);
            if (c <= ' ') {
                i++;
                continue;
            }
            if (c == '/' && i + 1 < to && s.charAt(i + 1) == '*') {
                int e = s.indexOf("*/", i + 2);
                e = (e < 0 || e + 2 > to) ? to : e + 2;
                t.add(i, e, Lexer.COM);
                i = e;
                continue;
            }
            if (L.lineC.length > 0 && c == '/' && i + 1 < to && s.charAt(i + 1) == '/') {
                int e = eol(s, i, to);
                t.add(i, e, Lexer.COM);
                i = e;
                continue;
            }
            if (c == '"' || c == '\'') {
                int e = scanString(s, i, to, c, false);
                t.add(i, e, Lexer.STR);
                i = e;
                continue;
            }
            if (c == '{') {
                depth++;
                i++;
                continue;
            }
            if (c == '}') {
                depth = Math.max(0, depth - 1);
                i++;
                continue;
            }
            if (c == '(') paren++;
            if (c == ')') paren = Math.max(0, paren - 1);

            if (c == '@' || c == '$' || c == '!') {
                int j = i + 1;
                while (j < to && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '-' || s.charAt(j) == '_')) j++;
                t.add(i, j, c == '$' ? Lexer.ATTR : Lexer.META);
                i = Math.max(j, i + 1);
                continue;
            }
            if (c == '#') {
                int j = i + 1;
                boolean hex = true;
                while (j < to && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '-' || s.charAt(j) == '_')) {
                    if (!isHex(s.charAt(j))) hex = false;
                    j++;
                }
                int len = j - i - 1;
                boolean color = depth > 0 && hex && (len == 3 || len == 4 || len == 6 || len == 8);
                t.add(i, j, color ? Lexer.NUM : Lexer.TAG);
                i = j;
                continue;
            }
            if (isDigit(c) || (c == '.' && i + 1 < to && isDigit(s.charAt(i + 1)))
                    || ((c == '-' || c == '+') && depth > 0 && i + 1 < to
                    && (isDigit(s.charAt(i + 1)) || s.charAt(i + 1) == '.'))) {
                int j = i + 1;
                while (j < to && (isDigit(s.charAt(j)) || s.charAt(j) == '.')) j++;
                while (j < to && (Character.isLetter(s.charAt(j)) || s.charAt(j) == '%')) j++;
                t.add(i, j, Lexer.NUM);
                i = j;
                continue;
            }
            if (depth == 0 && (c == '.' || c == ':' || c == '&' || c == '>' || c == '+' || c == '~' || c == '*')) {
                int j = i + 1;
                while (j < to && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '-' || s.charAt(j) == '_' || s.charAt(j) == ':')) j++;
                if (j > i + 1) t.add(i, j, Lexer.TAG);
                i = j;
                continue;
            }
            if (Character.isLetter(c) || c == '_' || c == '-') {
                int j = i + 1;
                while (j < to && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '-' || s.charAt(j) == '_')) j++;
                if (depth == 0) {
                    t.add(i, j, Lexer.TAG);
                } else if (paren == 0) {
                    int k = j;
                    while (k < to && (s.charAt(k) == ' ' || s.charAt(k) == '\t')) k++;
                    if (k < to && s.charAt(k) == ':') t.add(i, j, Lexer.ATTR);
                }
                i = j;
                continue;
            }
            i++;
        }
    }

    // ---------- Markdown / AsciiDoc ----------

    static void markdown(String s, Lang L, Tokens t) {
        int n = s.length();
        int i = 0;
        boolean fence = false;
        while (i < n) {
            int e = s.indexOf('\n', i);
            if (e < 0) e = n;
            int p = i;
            while (p < e && (s.charAt(p) == ' ' || s.charAt(p) == '\t')) p++;

            if (starts(s, p, e, "```") || starts(s, p, e, "~~~")) {
                t.add(i, e, Lexer.META);
                fence = !fence;
            } else if (fence) {
                t.add(i, e, Lexer.STR);
            } else if (p < e && (s.charAt(p) == '#' || (L.adoc && s.charAt(p) == '='))) {
                char h = s.charAt(p);
                int k = p;
                while (k < e && s.charAt(k) == h) k++;
                if (k - p <= 6 && k < e && s.charAt(k) == ' ') t.add(i, e, Lexer.HEAD);
                else inline(s, p, e, t);
            } else if (isRule(s, p, e)) {
                t.add(i, e, Lexer.META);
            } else {
                int q = p;
                if (q < e && s.charAt(q) == '>') {
                    t.add(q, q + 1, Lexer.META);
                    q++;
                    while (q < e && s.charAt(q) == ' ') q++;
                }
                if (q + 1 < e && "-*+".indexOf(s.charAt(q)) >= 0 && s.charAt(q + 1) == ' ') {
                    t.add(q, q + 1, Lexer.KW);
                    q += 2;
                } else {
                    int d = q;
                    while (d < e && isDigit(s.charAt(d))) d++;
                    if (d > q && d + 1 < e && (s.charAt(d) == '.' || s.charAt(d) == ')') && s.charAt(d + 1) == ' ') {
                        t.add(q, d + 1, Lexer.KW);
                        q = d + 2;
                    }
                }
                inline(s, q, e, t);
            }
            i = e + 1;
        }
    }

    private static boolean isRule(String s, int p, int e) {
        if (p >= e) return false;
        char c = s.charAt(p);
        if (c != '-' && c != '*' && c != '_') return false;
        int cnt = 0;
        for (int k = p; k < e; k++) {
            char ch = s.charAt(k);
            if (ch == c) cnt++;
            else if (ch != ' ') return false;
        }
        return cnt >= 3;
    }

    private static void inline(String s, int from, int to, Tokens t) {
        int j = from;
        while (j < to) {
            char c = s.charAt(j);
            if (c == '`') {
                int k = s.indexOf('`', j + 1);
                if (k > 0 && k < to) {
                    t.add(j, k + 1, Lexer.STR);
                    j = k + 1;
                    continue;
                }
            } else if (c == '*' && j + 1 < to && s.charAt(j + 1) == '*') {
                int k = s.indexOf("**", j + 2);
                if (k > 0 && k + 2 <= to) {
                    t.add(j, k + 2, Lexer.ATTR);
                    j = k + 2;
                    continue;
                }
            } else if (c == '[') {
                int k = s.indexOf(']', j + 1);
                if (k > 0 && k < to && k + 1 < to && s.charAt(k + 1) == '(') {
                    int m = s.indexOf(')', k + 2);
                    if (m > 0 && m < to) {
                        t.add(j, k + 1, Lexer.TAG);
                        t.add(k + 1, m + 1, Lexer.STR);
                        j = m + 1;
                        continue;
                    }
                }
            }
            j++;
        }
    }

    // ---------- diff ----------

    static void diff(String s, Tokens t) {
        int n = s.length();
        int i = 0;
        while (i < n) {
            int e = s.indexOf('\n', i);
            if (e < 0) e = n;
            if (e > i) {
                char c = s.charAt(i);
                if (s.startsWith("+++", i) || s.startsWith("---", i) || s.startsWith("diff ", i)
                        || s.startsWith("index ", i)) {
                    t.add(i, e, Lexer.HEAD);
                } else if (c == '+') {
                    t.add(i, e, Lexer.ADD);
                } else if (c == '-') {
                    t.add(i, e, Lexer.DEL);
                } else if (c == '@') {
                    t.add(i, e, Lexer.META);
                }
            }
            i = e + 1;
        }
    }

    // ---------- INI / TOML / properties ----------

    static void ini(String s, Tokens t) {
        Lang value = Langs.VALUE_INI;
        int n = s.length();
        int i = 0;
        while (i < n) {
            int e = s.indexOf('\n', i);
            if (e < 0) e = n;
            int p = i;
            while (p < e && (s.charAt(p) == ' ' || s.charAt(p) == '\t')) p++;
            if (p < e) {
                char c = s.charAt(p);
                if (c == ';' || c == '#') {
                    t.add(p, e, Lexer.COM);
                } else if (c == '[') {
                    t.add(p, e, Lexer.HEAD);
                } else {
                    int k = p;
                    while (k < e && s.charAt(k) != '=' && s.charAt(k) != ':') k++;
                    if (k < e) {
                        int ke = k;
                        while (ke > p && s.charAt(ke - 1) <= ' ') ke--;
                        t.add(p, ke, Lexer.ATTR);
                        generic(s, k + 1, e, value, t);
                    }
                }
            }
            i = e + 1;
        }
    }

    // ---------- YAML ----------

    static void yaml(String s, Tokens t) {
        Lang value = Langs.VALUE_YAML;
        int n = s.length();
        int i = 0;
        while (i < n) {
            int e = s.indexOf('\n', i);
            if (e < 0) e = n;
            int p = i;
            while (p < e && (s.charAt(p) == ' ' || s.charAt(p) == '\t')) p++;
            if (p < e) {
                char c = s.charAt(p);
                if (c == '#') {
                    t.add(p, e, Lexer.COM);
                } else if (starts(s, p, e, "---") || starts(s, p, e, "...")) {
                    t.add(p, Math.min(e, p + 3), Lexer.META);
                    generic(s, p + 3, e, value, t);
                } else {
                    while (p < e && s.charAt(p) == '-' && p + 1 < e && s.charAt(p + 1) == ' ') {
                        t.add(p, p + 1, Lexer.KW);
                        p += 2;
                        while (p < e && s.charAt(p) == ' ') p++;
                    }
                    int keyEnd = -1;
                    int k = p;
                    if (k < e && (s.charAt(k) == '"' || s.charAt(k) == '\'')) {
                        k = scanString(s, k, e, s.charAt(k), false);
                        if (k < e && s.charAt(k) == ':') keyEnd = k;
                    } else {
                        while (k < e) {
                            char ch = s.charAt(k);
                            if (ch == ':' && (k + 1 >= e || s.charAt(k + 1) == ' ')) {
                                keyEnd = k;
                                break;
                            }
                            if (ch == '#' && k > p && s.charAt(k - 1) == ' ') break;
                            k++;
                        }
                    }
                    if (keyEnd > p) {
                        t.add(p, keyEnd, Lexer.ATTR);
                        generic(s, keyEnd + 1, e, value, t);
                    } else {
                        generic(s, p, e, value, t);
                    }
                }
            }
            i = e + 1;
        }
    }

    // ---------- log ----------

    private static int logClass(String w) {
        switch (w) {
            case "ERROR":
            case "FATAL":
            case "SEVERE":
            case "CRITICAL":
                return Lexer.DEL;
            case "WARN":
            case "WARNING":
                return Lexer.META;
            case "INFO":
                return Lexer.TAG;
            case "DEBUG":
            case "TRACE":
                return Lexer.COM;
            default:
                return 0;
        }
    }

    /** Tô màu mức log (ERROR, WARN, INFO...) — quét tuyến tính, mỗi dòng tối đa một từ. */
    static void log(String s, Tokens t) {
        int n = s.length();
        int i = 0;
        while (i < n) {
            int e = s.indexOf('\n', i);
            if (e < 0) e = n;
            int j = i;
            while (j < e) {
                char c = s.charAt(j);
                if (c >= 'A' && c <= 'Z' && (j == i || !Character.isLetter(s.charAt(j - 1)))) {
                    int k = j + 1;
                    while (k < e && s.charAt(k) >= 'A' && s.charAt(k) <= 'Z') k++;
                    if (k - j >= 4 && k - j <= 8) {
                        int cls = logClass(s.substring(j, k));
                        if (cls != 0) {
                            t.add(j, k, cls);
                            break;
                        }
                    }
                    j = k;
                } else {
                    j++;
                }
            }
            i = e + 1;
        }
    }
}
