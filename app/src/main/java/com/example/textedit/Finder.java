package com.example.textedit;

import java.util.regex.Pattern;

/** Dựng mẫu tìm kiếm từ các tùy chọn: phân biệt hoa thường, Regex, chỉ khớp toàn từ. */
final class Finder {
    private static final String B_BEFORE = "(?<![\\p{L}\\p{N}_])";
    private static final String B_AFTER = "(?![\\p{L}\\p{N}_])";

    private Finder() { }

    /** @throws java.util.regex.PatternSyntaxException nếu Regex sai. Trả về null nếu chuỗi tìm rỗng. */
    static Pattern build(String query, boolean matchCase, boolean regex, boolean wholeWord) {
        if (query == null || query.isEmpty()) return null;
        int flags = 0;
        if (!matchCase) flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        String body;
        if (regex) {
            flags |= Pattern.MULTILINE;
            body = wholeWord ? B_BEFORE + "(?:" + query + ")" + B_AFTER : query;
        } else {
            body = Pattern.quote(query);
            if (wholeWord) body = B_BEFORE + body + B_AFTER;
        }
        return Pattern.compile(body, flags);
    }
}
