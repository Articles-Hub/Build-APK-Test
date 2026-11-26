package com.example.textedit;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Biên dịch và chạy mã qua một máy chủ Judge0 (mặc định ce.judge0.com, có thể đổi trong Cài đặt).
 * Mã nguồn được gửi lên máy chủ đó, nên ứng dụng luôn hỏi sự đồng ý của người dùng trước.
 */
final class OnlineCompiler {

    /** Tên ngôn ngữ trong app -> tiền tố tên ngôn ngữ trên Judge0. */
    private static final Map<String, String> PREFIX = new HashMap<>();

    static {
        String[][] m = {
                {"C", "C ("}, {"C++", "C++ ("}, {"C#", "C# ("}, {"Java", "Java ("}, {"Python", "Python ("},
                {"JavaScript", "JavaScript ("}, {"TypeScript", "TypeScript ("}, {"Go", "Go ("},
                {"Rust", "Rust ("}, {"PHP", "PHP ("}, {"Ruby", "Ruby ("}, {"Kotlin", "Kotlin ("},
                {"Swift", "Swift ("}, {"Scala", "Scala ("}, {"Perl", "Perl ("}, {"Lua", "Lua ("},
                {"Haskell", "Haskell ("}, {"R", "R ("}, {"Dart", "Dart ("}, {"Shell", "Bash ("},
                {"SQL", "SQL ("}, {"Pascal", "Pascal ("}, {"Fortran", "Fortran ("}, {"D", "D ("},
                {"Elixir", "Elixir ("}, {"Erlang", "Erlang ("}, {"Clojure", "Clojure ("},
                {"Common Lisp", "Common Lisp ("}, {"OCaml", "OCaml ("}, {"MATLAB", "Octave ("},
                {"Prolog", "Prolog ("}, {"Groovy", "Groovy ("}, {"F#", "F# ("},
                {"Visual Basic", "Visual Basic"}, {"COBOL", "COBOL ("}, {"Objective-C", "Objective-C ("},
                {"Assembly", "Assembly ("}, {"QBasic", "Basic ("}
        };
        for (String[] e : m) PREFIX.put(e[0], e[1]);
    }

    private static JSONArray languagesCache;
    private static String languagesCacheBase;

    private OnlineCompiler() { }

    /** Ngôn ngữ trong app có thể chạy online hay không (còn tùy máy chủ có hỗ trợ). */
    static boolean supports(String langName) {
        return langName != null && PREFIX.containsKey(langName);
    }

    static int supportedCount() {
        return PREFIX.size();
    }

    static java.util.List<String> supportedNames() {
        java.util.ArrayList<String> out = new java.util.ArrayList<>(PREFIX.keySet());
        java.util.Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    private static Map<String, String> headers(String token) {
        HashMap<String, String> h = new HashMap<>();
        h.put("Content-Type", "application/json");
        h.put("Accept", "application/json");
        if (token != null && !token.isEmpty()) h.put("X-Auth-Token", token);
        return h;
    }

    private static String trimBase(String base) {
        String b = base.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return b;
    }

    private static synchronized JSONArray languages(String base, String token) throws Exception {
        if (languagesCache != null && base.equals(languagesCacheBase)) return languagesCache;
        Net.Response r = Net.http("GET", base + "/languages", headers(token), null);
        if (r.code != 200) throw new IOException("HTTP " + r.code + ": " + Net.text(r.body));
        languagesCache = new JSONArray(Net.text(r.body));
        languagesCacheBase = base;
        return languagesCache;
    }

    /** Chọn id ngôn ngữ theo tên: lấy phiên bản mới nhất khớp tiền tố, bỏ qua các biến thể kiểm thử. */
    private static int resolveId(JSONArray langs, String prefix) throws Exception {
        int best = -1;
        for (int i = 0; i < langs.length(); i++) {
            JSONObject o = langs.getJSONObject(i);
            String name = o.getString("name");
            if (!name.startsWith(prefix)) continue;
            if (name.contains("JUnit") || name.contains("Test") || name.contains("Multi-file")
                    || name.contains("Executable")) continue;
            best = Math.max(best, o.getInt("id"));
        }
        return best;
    }

    /** Chạy mã và trả về báo cáo dạng văn bản. Gọi từ luồng nền. */
    static String execute(String baseUrl, String token, String langName, String code, String stdin)
            throws Exception {
        String base = trimBase(baseUrl);
        String prefix = PREFIX.get(langName);
        if (prefix == null) throw new IOException("Language not supported for online run: " + langName);
        int id = resolveId(languages(base, token), prefix);
        if (id < 0) throw new IOException("The server has no matching language for " + langName);

        JSONObject body = new JSONObject();
        body.put("source_code", code);
        body.put("language_id", id);
        if (stdin != null && !stdin.isEmpty()) body.put("stdin", stdin);
        Net.Response r = Net.http("POST", base + "/submissions?base64_encoded=false&wait=true",
                headers(token), body.toString().getBytes("UTF-8"));
        if (r.code >= 400) throw new IOException("HTTP " + r.code + ": " + Net.text(r.body));
        JSONObject res = new JSONObject(Net.text(r.body));

        // Nếu máy chủ không bật wait=true thì chỉ trả về token: hỏi lại cho tới khi xong.
        String token2 = res.optString("token", "");
        for (int i = 0; i < 40 && !isFinished(res) && !token2.isEmpty(); i++) {
            Thread.sleep(1000);
            Net.Response p = Net.http("GET", base + "/submissions/" + token2 + "?base64_encoded=false&fields=*",
                    headers(token), null);
            if (p.code >= 400) throw new IOException("HTTP " + p.code + ": " + Net.text(p.body));
            res = new JSONObject(Net.text(p.body));
        }
        return format(res);
    }

    private static boolean isFinished(JSONObject res) {
        JSONObject st = res.optJSONObject("status");
        return st != null && st.optInt("id", 0) > 2;
    }

    private static String format(JSONObject res) {
        StringBuilder sb = new StringBuilder();
        JSONObject st = res.optJSONObject("status");
        if (st != null) sb.append("[").append(st.optString("description", "?")).append("]\n");
        append(sb, "compile", res.optString("compile_output", ""));
        append(sb, "stdout", res.optString("stdout", ""));
        append(sb, "stderr", res.optString("stderr", ""));
        append(sb, "message", res.optString("message", ""));
        String time = res.optString("time", "");
        String mem = res.optString("memory", "");
        if (!"null".equals(time) && !time.isEmpty()) sb.append("\ntime: ").append(time).append(" s");
        if (!"null".equals(mem) && !mem.isEmpty()) sb.append("   memory: ").append(mem).append(" KB");
        return sb.toString();
    }

    private static void append(StringBuilder sb, String label, String v) {
        if (v == null || v.isEmpty() || v.equals("null")) return;
        sb.append("--- ").append(label).append(" ---\n").append(v);
        if (!v.endsWith("\n")) sb.append('\n');
    }
}
