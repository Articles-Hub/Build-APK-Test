package com.example.textedit;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;

/** Khóa cài đặt và tiện ích áp dụng ngôn ngữ, giao diện sáng/tối. */
final class Prefs {
    static final String FILE = "settings";

    static final String FONT = "font";               // monospace | sans | serif | casual | cursive | custom
    static final String FONT_SIZE = "font_size";     // sp
    static final String TEXT_COLOR = "text_color";   // ARGB int; không có = tự động theo giao diện
    static final String OVERLAY = "overlay";         // 0..100 (%)
    static final String BG_TYPE = "bg_type";         // none | color | image | video
    static final String BG_VALUE = "bg_value";       // màu (int) hoặc uri/link
    static final String LANG = "lang";               // mã ngôn ngữ ISO, rỗng = theo hệ thống
    static final String TOOLBAR = "toolbar";
    static final String WRAP = "wrap";
    static final String LINES = "line_numbers";
    static final String THEME = "theme";             // system | light | dark
    static final String HIGHLIGHT = "highlight";
    static final String AUTOINDENT = "auto_indent";
    static final String TAB_SIZE = "tab_size";
    static final String COMPILER_URL = "compiler_url";
    static final String COMPILER_TOKEN = "compiler_token";
    static final String COMPILER_OK = "compiler_consent"; // máy chủ compiler đã được người dùng đồng ý
    static final String DEFAULT_COMPILER = "https://ce.judge0.com";

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

    static boolean isDark(Context c) {
        String t = get(c).getString(THEME, "system");
        if ("dark".equals(t)) return true;
        if ("light".equals(t)) return false;
        // Dùng cấu hình hệ thống để luôn phản ánh chế độ sáng/tối hiện tại của thiết bị.
        int ui = Resources.getSystem().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return ui == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Gọi trước super.onCreate để áp dụng giao diện sáng/tối. */
    static void applyTheme(Activity a) {
        a.setTheme(isDark(a) ? android.R.style.Theme_Material : android.R.style.Theme_Material_Light_DarkActionBar);
    }

    static int defaultTextColor(Context c) {
        return isDark(c) ? 0xFFE0E0E0 : 0xFF000000;
    }

    /** Khi khóa này thay đổi, Activity cần tạo lại để áp dụng ngôn ngữ hoặc giao diện mới. */
    static String configKey(Context c) {
        SharedPreferences p = get(c);
        return p.getString(LANG, "") + "|" + isDark(c);
    }
}
