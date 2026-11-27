package com.example.textedit;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lưu các thẻ đang mở (kể cả nội dung chưa lưu) vào bộ nhớ riêng của app để mở lại app là có đủ.
 * Lịch sử hoàn tác không được lưu.
 */
final class Session {

    static final class Item {
        String type = "none"; // saf | root | none
        String ref = "";
        String name = "";
        String charset = "UTF-8";
        boolean bom;
        String eol = Eol.LF;
        boolean readOnly;
        boolean dirty;
        int selStart;
        int selEnd;
        int scrollY;
        String lang;
        String text = "";
    }

    static final class Snapshot {
        final ArrayList<Item> items = new ArrayList<>();
        int cur;
    }

    /** Tổng số ký tự tối đa lưu cho các thẻ không có thay đổi (các thẻ có thay đổi luôn được lưu). */
    private static final long CAP = 48L * 1024 * 1024;
    private static final ExecutorService EX = Executors.newSingleThreadExecutor();
    private static final Object LOCK = new Object();

    private Session() { }

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "session");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Chụp trạng thái hiện tại. Gọi ở luồng giao diện sau khi đã cập nhật Doc.text của thẻ đang mở. */
    static Snapshot capture(List<Doc> docs, int cur) {
        Snapshot s = new Snapshot();
        long total = 0;
        for (Doc d : docs) {
            if (d.dirty() || d.backend == null) total += d.text.length();
        }
        int newCur = 0;
        for (int i = 0; i < docs.size(); i++) {
            Doc d = docs.get(i);
            boolean dirty = d.dirty();
            String[] ref = d.backend == null ? null : Recent.describe(d.backend);
            boolean detached = d.backend != null && ref == null; // máy chủ/Git: không khôi phục được kết nối
            boolean mustKeep = dirty || d.backend == null || detached;
            if (!mustKeep) {
                if (total + d.text.length() > CAP) continue;
                total += d.text.length();
            }
            if (!dirty && d.backend == null && d.text.isEmpty() && docs.size() > 1) continue; // thẻ trống không cần giữ
            Item it = new Item();
            if (ref != null) {
                it.type = ref[0];
                it.ref = ref[1];
            }
            it.name = d.fileName();
            it.charset = d.charset.name();
            it.bom = d.bom;
            it.eol = d.eol;
            it.readOnly = d.readOnly;
            it.dirty = dirty || detached;
            it.selStart = d.selStart;
            it.selEnd = d.selEnd;
            it.scrollY = d.scrollY;
            it.lang = d.langOverride;
            it.text = d.text;
            if (i == cur) newCur = s.items.size();
            s.items.add(it);
        }
        s.cur = newCur;
        boolean trivial = s.items.size() == 1 && s.items.get(0).type.equals("none")
                && s.items.get(0).text.isEmpty() && !s.items.get(0).dirty;
        if (trivial) s.items.clear();
        return s;
    }

    /** Ghi ở luồng nền; các lần ghi được xếp hàng nên lần sau cùng luôn thắng. */
    static void saveAsync(final Context app, final Snapshot snap) {
        EX.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (LOCK) {
                        write(app, snap);
                    }
                } catch (Exception ignored) {
                    // không lưu được phiên thì lần mở sau sẽ bắt đầu trống
                }
            }
        });
    }

    static void clearAsync(final Context app) {
        EX.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (LOCK) {
                        File d = dir(app);
                        File[] fs = d.listFiles();
                        if (fs != null) for (File f : fs) f.delete();
                    }
                } catch (Exception ignored) {
                    // bỏ qua
                }
            }
        });
    }

    private static void write(Context app, Snapshot snap) throws Exception {
        File d = dir(app);
        if (snap.items.isEmpty()) {
            File[] fs = d.listFiles();
            if (fs != null) for (File f : fs) f.delete();
            return;
        }
        long gen = System.currentTimeMillis();
        JSONArray arr = new JSONArray();
        HashSet<String> keep = new HashSet<>();
        keep.add("index.json");
        for (int i = 0; i < snap.items.size(); i++) {
            Item it = snap.items.get(i);
            String fname = "t" + gen + "_" + i + ".txt";
            Writer w = new OutputStreamWriter(new FileOutputStream(new File(d, fname)), "UTF-8");
            try {
                w.write(it.text);
            } finally {
                w.close();
            }
            keep.add(fname);
            JSONObject o = new JSONObject();
            o.put("t", it.type);
            o.put("r", it.ref);
            o.put("n", it.name);
            o.put("cs", it.charset);
            o.put("bom", it.bom);
            o.put("eol", it.eol);
            o.put("ro", it.readOnly);
            o.put("dirty", it.dirty);
            o.put("ss", it.selStart);
            o.put("se", it.selEnd);
            o.put("sy", it.scrollY);
            if (it.lang != null) o.put("lang", it.lang);
            o.put("f", fname);
            arr.put(o);
        }
        JSONObject root = new JSONObject();
        root.put("cur", snap.cur);
        root.put("items", arr);
        File tmp = new File(d, "index.tmp");
        Writer w = new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8");
        try {
            w.write(root.toString());
        } finally {
            w.close();
        }
        File idx = new File(d, "index.json");
        if (!tmp.renameTo(idx)) {
            idx.delete();
            if (!tmp.renameTo(idx)) throw new java.io.IOException("rename failed");
        }
        File[] fs = d.listFiles();
        if (fs != null) {
            for (File f : fs) if (!keep.contains(f.getName())) f.delete();
        }
    }

    /** Đọc phiên đã lưu thành danh sách Doc; trả về null nếu không có. */
    static ArrayList<Doc> load(Context ctx, int[] curOut) {
        // Chờ các lần ghi đang xếp hàng (vừa thoát app rồi mở lại ngay) xong rồi mới đọc.
        try {
            EX.submit(new Runnable() {
                @Override
                public void run() {
                    // chỉ để xả hàng đợi
                }
            }).get();
        } catch (Exception ignored) {
            // vẫn thử đọc
        }
        synchronized (LOCK) {
            return loadLocked(ctx, curOut);
        }
    }

    private static ArrayList<Doc> loadLocked(Context ctx, int[] curOut) {
        try {
            File d = dir(ctx);
            File idx = new File(d, "index.json");
            if (!idx.exists()) return null;
            JSONObject root = new JSONObject(readFile(idx));
            JSONArray arr = root.getJSONArray("items");
            ArrayList<Doc> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                File tf = new File(d, o.getString("f"));
                if (!tf.exists()) continue;
                Doc doc = new Doc();
                doc.text = readFile(tf);
                String type = o.optString("t", "none");
                String ref = o.optString("r", "");
                doc.backend = "none".equals(type) ? null : Recent.make(ctx.getApplicationContext(), type, ref);
                doc.name = o.optString("n", "");
                try {
                    doc.charset = Charset.forName(o.optString("cs", "UTF-8"));
                } catch (Exception e) {
                    doc.charset = Charset.forName("UTF-8");
                }
                doc.bom = o.optBoolean("bom", false);
                doc.eol = o.optString("eol", Eol.LF);
                doc.readOnly = o.optBoolean("ro", false);
                doc.forceDirty = o.optBoolean("dirty", false);
                doc.selStart = o.optInt("ss", 0);
                doc.selEnd = o.optInt("se", 0);
                doc.scrollY = o.optInt("sy", 0);
                doc.langOverride = o.has("lang") ? o.getString("lang") : null;
                if (doc.backend == null && doc.name.isEmpty()) doc.name = null;
                out.add(doc);
            }
            if (out.isEmpty()) return null;
            curOut[0] = Math.max(0, Math.min(root.optInt("cur", 0), out.size() - 1));
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static String readFile(File f) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"), 1 << 16);
        try {
            StringBuilder sb = new StringBuilder((int) Math.min(f.length(), 1 << 26));
            char[] buf = new char[1 << 16];
            int n;
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
            return sb.toString();
        } finally {
            r.close();
        }
    }
}
