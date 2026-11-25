package com.example.textedit;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import java.util.Locale;

/** Khóa cài đặt và tiện ích áp dụng ngôn ngữ cho Activity. */
final class Prefs {
    static final String FILE = "settings";

    static final String FONT = "font";               // monospace | sans | serif | casual | cursive | custom
    static final String FONT_SIZE = "font_size";     // sp
    static final String TEXT_COLOR = "text_color";   // ARGB int
    static final String OVERLAY = "overlay";         // 0..100 (%)
    static final String BG_TYPE = "bg_type";         // none | color | image | video
    static final String BG_VALUE = "bg_value";       // màu (int) hoặc uri/link
    static final String LANG = "lang";               // mã ngôn ngữ ISO, rỗng = theo hệ thống
    static final String TOOLBAR = "toolbar";
    static final String WRAP = "wrap";
    static final String LINES = "line_numbers";

    private Prefs() { }

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Bọc context để dùng ngôn ngữ đã chọn trong app. */
    static Context wrap(Context base) {
        String tag = get(base).getString(LANG, "");
        if (tag == null || tag.isEmpty()) return base;
        Locale locale = new Locale(tag);
        Locale.setDefault(locale);
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        cfg.setLocale(locale);
        return base.createConfigurationContext(cfg);
    }
}
