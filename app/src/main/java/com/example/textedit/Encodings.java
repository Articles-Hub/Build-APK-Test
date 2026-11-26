package com.example.textedit;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.List;

/** Nhận diện, giải mã và mã hóa văn bản theo nhiều bảng mã. Việc nhận diện chỉ là suy đoán. */
final class Encodings {

    static final class Result {
        final Charset charset;
        final int bomLength;

        Result(Charset cs, int bom) {
            charset = cs;
            bomLength = bom;
        }
    }

    private static final String[] CHOICES = {
            "UTF-8", "UTF-16LE", "UTF-16BE", "UTF-32LE", "UTF-32BE",
            "US-ASCII", "ISO-8859-1", "ISO-8859-2", "ISO-8859-3", "ISO-8859-4", "ISO-8859-5",
            "ISO-8859-6", "ISO-8859-7", "ISO-8859-8", "ISO-8859-9", "ISO-8859-13", "ISO-8859-15",
            "windows-1250", "windows-1251", "windows-1252", "windows-1253", "windows-1254",
            "windows-1255", "windows-1256", "windows-1257", "windows-1258", "windows-874",
            "KOI8-R", "KOI8-U", "IBM866", "IBM437", "IBM850", "x-MacRoman", "TIS-620",
            "Shift_JIS", "windows-31j", "EUC-JP", "ISO-2022-JP",
            "GB18030", "GBK", "GB2312", "Big5", "Big5-HKSCS",
            "EUC-KR", "x-windows-949", "ISO-2022-KR"
    };

    private Encodings() { }

    /** Danh sách bảng mã mà thiết bị hỗ trợ. */
    static List<String> choices() {
        ArrayList<String> out = new ArrayList<>();
        for (String n : CHOICES) {
            try {
                if (Charset.isSupported(n)) out.add(n);
            } catch (Exception ignored) {
                // tên không hợp lệ: bỏ qua
            }
        }
        return out;
    }

    static Charset forName(String name) {
        try {
            return Charset.forName(name);
        } catch (Exception e) {
            return Charset.forName("UTF-8");
        }
    }

    static Result detect(byte[] b) {
        int n = b.length;
        if (n >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) {
            return new Result(forName("UTF-8"), 3);
        }
        if (n >= 4 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE && b[2] == 0 && b[3] == 0
                && Charset.isSupported("UTF-32LE")) {
            return new Result(forName("UTF-32LE"), 4);
        }
        if (n >= 4 && b[0] == 0 && b[1] == 0 && (b[2] & 0xFF) == 0xFE && (b[3] & 0xFF) == 0xFF
                && Charset.isSupported("UTF-32BE")) {
            return new Result(forName("UTF-32BE"), 4);
        }
        if (n >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE) {
            return new Result(forName("UTF-16LE"), 2);
        }
        if (n >= 2 && (b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xFF) {
            return new Result(forName("UTF-16BE"), 2);
        }
        if (n == 0) return new Result(forName("UTF-8"), 0);

        // UTF-16 không BOM: văn bản Latin có byte 0 ở vị trí chẵn hoặc lẻ.
        int sample = Math.min(n, 8192);
        int evenZero = 0;
        int oddZero = 0;
        for (int i = 0; i < sample; i++) {
            if (b[i] == 0) {
                if ((i & 1) == 0) evenZero++;
                else oddZero++;
            }
        }
        int half = sample / 2;
        if (half > 8) {
            if (oddZero > half * 0.3 && evenZero < half * 0.05) return new Result(forName("UTF-16LE"), 0);
            if (evenZero > half * 0.3 && oddZero < half * 0.05) return new Result(forName("UTF-16BE"), 0);
        }

        boolean high = false;
        boolean esc = false;
        for (int i = 0; i < n; i++) {
            int v = b[i] & 0xFF;
            if (v >= 0x80) high = true;
            else if (v == 0x1B) esc = true;
        }
        if (esc && !high && contains(b, new byte[]{0x1B, '$', 'B'}, new byte[]{0x1B, '(', 'B'}, new byte[]{0x1B, '$', '@'})
                && Charset.isSupported("ISO-2022-JP")) {
            return new Result(forName("ISO-2022-JP"), 0);
        }
        if (!high) return new Result(forName("UTF-8"), 0);
        if (strictOk("UTF-8", b)) return new Result(forName("UTF-8"), 0);

        return detectLegacy(b);
    }

    /**
     * Phân tích mẫu byte cho các bảng mã cũ. Đây chỉ là suy đoán dựa trên thống kê;
     * người dùng luôn có thể chọn lại bảng mã thủ công.
     */
    private static Result detectLegacy(byte[] b) {
        int n = b.length;
        int hi = 0;
        int cyr = 0;      // byte C0-FF
        int lower = 0;    // byte E0-FF
        int adjacent = 0;
        for (int i = 0; i < n; i++) {
            int v = b[i] & 0xFF;
            if (v >= 0x80) {
                hi++;
                if (v >= 0xC0) cyr++;
                if (v >= 0xE0) lower++;
                if (i + 1 < n && (b[i + 1] & 0x80) != 0) adjacent++;
            }
        }

        // Cyrillic một byte: byte cao đứng liền thành từ, tập trung ở dải chữ thường E0-FF.
        if (hi >= 6 && adjacent > hi * 0.6 && cyr > hi * 0.9 && lower > hi * 0.45
                && Charset.isSupported("windows-1251")) {
            return new Result(forName("windows-1251"), 0);
        }

        // Thống kê cặp byte (đầu, đuôi) của các bảng mã hai byte.
        int pairs = 0;
        int lowTrail = 0;
        int kanaSjis = 0;
        int kanaEuc = 0;
        int leadA4C6 = 0;
        int leadB0C8 = 0;
        int leadC9 = 0;
        for (int i = 0; i + 1 < n; ) {
            int c = b[i] & 0xFF;
            if (c < 0x81) {
                i++;
                continue;
            }
            int d = b[i + 1] & 0xFF;
            if (d < 0x40) {
                i++;
                continue;
            }
            pairs++;
            if (d <= 0x7E) lowTrail++;
            if ((c == 0x82 && d >= 0x9F && d <= 0xF1) || (c == 0x83 && d >= 0x40 && d <= 0x96)) kanaSjis++;
            if ((c == 0xA4 && d >= 0xA1 && d <= 0xF3) || (c == 0xA5 && d >= 0xA1 && d <= 0xF6)) kanaEuc++;
            if (c >= 0xA4 && c <= 0xC6) leadA4C6++;
            if (c >= 0xB0 && c <= 0xC8) leadB0C8++;
            if (c >= 0xC9 && c <= 0xFE) leadC9++;
            i += 2;
        }

        if (pairs > 0) {
            // Tiếng Nhật luôn có nhiều kana: dấu hiệu mạnh nhất.
            if (kanaSjis >= 3 && kanaSjis >= pairs * 0.2 && strictOk("Shift_JIS", b)) {
                return new Result(forName("Shift_JIS"), 0);
            }
            if (kanaEuc >= 3 && kanaEuc >= pairs * 0.2 && strictOk("EUC-JP", b)
                    && lowTrail == 0) {
                return new Result(forName("EUC-JP"), 0);
            }
            // Văn bản CJK có mật độ byte cao; tiếng Latin có dấu thì thấp hơn nhiều.
            if (hi >= n * 0.2) {
                if (lowTrail >= pairs * 0.1) {
                    if (leadA4C6 >= pairs * 0.85 && strictOk("Big5", b)) {
                        return new Result(forName("Big5"), 0);
                    }
                    if (strictOk("GB18030", b)) return new Result(forName("GB18030"), 0);
                    if (strictOk("Shift_JIS", b)) return new Result(forName("Shift_JIS"), 0);
                } else {
                    if (leadB0C8 >= pairs * 0.85 && leadC9 < pairs * 0.08 && strictOk("EUC-KR", b)) {
                        return new Result(forName("EUC-KR"), 0);
                    }
                    if (strictOk("GB18030", b)) return new Result(forName("GB18030"), 0);
                }
            }
        }
        return new Result(forName(Charset.isSupported("windows-1252") ? "windows-1252" : "ISO-8859-1"), 0);
    }

    static String decode(byte[] b, Charset cs, int skip) {
        int off = Math.min(skip, b.length);
        return new String(b, off, b.length - off, cs);
    }

    /** Mã hóa văn bản; thêm BOM nếu cần (Java không tự thêm BOM cho UTF-16LE/BE và UTF-8). */
    static byte[] encode(String text, Charset cs, boolean bom) {
        byte[] body = text.getBytes(cs);
        if (!bom) return body;
        byte[] mark = bomFor(cs);
        if (mark.length == 0) return body;
        byte[] out = new byte[mark.length + body.length];
        System.arraycopy(mark, 0, out, 0, mark.length);
        System.arraycopy(body, 0, out, mark.length, body.length);
        return out;
    }

    static byte[] bomFor(Charset cs) {
        String n = cs.name().toUpperCase();
        switch (n) {
            case "UTF-8":
                return new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
            case "UTF-16LE":
                return new byte[]{(byte) 0xFF, (byte) 0xFE};
            case "UTF-16BE":
                return new byte[]{(byte) 0xFE, (byte) 0xFF};
            case "UTF-32LE":
                return new byte[]{(byte) 0xFF, (byte) 0xFE, 0, 0};
            case "UTF-32BE":
                return new byte[]{0, 0, (byte) 0xFE, (byte) 0xFF};
            default:
                return new byte[0];
        }
    }

    // ---------- nội bộ ----------

    private static boolean strictOk(String csName, byte[] b) {
        return strictDecode(csName, b) != null;
    }

    private static String strictDecode(String csName, byte[] b) {
        try {
            CharsetDecoder d = Charset.forName(csName).newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return d.decode(ByteBuffer.wrap(b)).toString();
        } catch (CharacterCodingException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean contains(byte[] b, byte[]... pats) {
        for (byte[] p : pats) {
            for (int i = 0; i + p.length <= b.length; i++) {
                boolean ok = true;
                for (int j = 0; j < p.length; j++) {
                    if (b[i + j] != p[j]) {
                        ok = false;
                        break;
                    }
                }
                if (ok) return true;
            }
        }
        return false;
    }
}
