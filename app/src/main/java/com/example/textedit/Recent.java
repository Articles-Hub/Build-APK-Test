package com.example.textedit;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Danh sách file mở gần đây (chỉ file trên thiết bị/đám mây qua SAF và file root; máy chủ cần mật khẩu nên không lưu). */
final class Recent {

    static final class Entry {
        String type; // "saf" | "root"
        String ref;  // uri hoặc đường dẫn
        String name;
    }

    private static final int MAX = 25;

    private Recent() { }

    /** Mô tả một Backend để lưu lại; null nếu không lưu được (máy chủ, Git). */
    static String[] describe(Backend b) {
        if (b instanceof Backends.Saf) return new String[]{"saf", ((Backends.Saf) b).uri.toString()};
        if (b instanceof Backends.Root) return new String[]{"root", ((Backends.Root) b).path};
        return null;
    }

    static Backend make(Context c, String type, String ref) {
        if ("saf".equals(type)) return new Backends.Saf(c, Uri.parse(ref));
        if ("root".equals(type)) return new Backends.Root(ref);
        return null;
    }

    static List<Entry> list(Context c) {
        ArrayList<Entry> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(Prefs.get(c).getString(Prefs.RECENT, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Entry e = new Entry();
                e.type = o.getString("t");
                e.ref = o.getString("r");
                e.name = o.optString("n", e.ref);
                out.add(e);
            }
        } catch (Exception ignored) {
            // danh sách hỏng thì coi như rỗng
        }
        return out;
    }

    static void add(Context c, Backend b) {
        String[] d = describe(b);
        if (d == null) return;
        List<Entry> cur = list(c);
        JSONArray a = new JSONArray();
        try {
            JSONObject first = new JSONObject();
            first.put("t", d[0]);
            first.put("r", d[1]);
            first.put("n", b.name() == null ? d[1] : b.name());
            a.put(first);
            for (Entry e : cur) {
                if (e.type.equals(d[0]) && e.ref.equals(d[1])) continue;
                if (a.length() >= MAX) break;
                JSONObject o = new JSONObject();
                o.put("t", e.type);
                o.put("r", e.ref);
                o.put("n", e.name);
                a.put(o);
            }
            Prefs.get(c).edit().putString(Prefs.RECENT, a.toString()).apply();
        } catch (Exception ignored) {
            // không ghi được thì bỏ qua
        }
    }

    static void clear(Context c) {
        SharedPreferences.Editor ed = Prefs.get(c).edit();
        ed.remove(Prefs.RECENT);
        ed.apply();
    }
}
