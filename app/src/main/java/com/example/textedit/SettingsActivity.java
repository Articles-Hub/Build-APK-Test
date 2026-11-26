package com.example.textedit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Màn hình cài đặt: giao diện, font, hình nền, ngôn ngữ, soạn thảo, trình biên dịch online. */
public class SettingsActivity extends Activity {

    private static final int REQ_FONT = 10;
    private static final int REQ_IMAGE = 11;
    private static final int REQ_VIDEO = 12;

    private static final String[] FONT_KEYS = {"monospace", "sans", "serif", "casual", "cursive"};
    private static final String[] FONT_NAMES = {"Monospace", "Sans-serif", "Serif", "Casual", "Cursive"};
    private static final int[] SIZES = {10, 12, 14, 15, 16, 18, 20, 24, 28, 32};
    private static final int[] OVERLAYS = {0, 25, 50, 75, 90, 100};
    private static final int[] PALETTE = {
            0xFF000000, 0xFFFFFFFF, 0xFF424242, 0xFF9E9E9E,
            0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047,
            0xFF00ACC1, 0xFF1E88E5, 0xFF8E24AA, 0xFFEC407A,
            0xFF6D4C41, 0xFFFFF8E1, 0xFF1B1B2F, 0xFFB3E5FC};

    private interface IntCallback {
        void on(int value);
    }

    private interface StringCallback {
        void on(String value);
    }

    private SharedPreferences p;
    private LinearLayout list;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Prefs.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Prefs.applyTheme(this);
        super.onCreate(savedInstanceState);
        setTitle(R.string.action_settings);
        p = Prefs.get(this);
        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        setContentView(sv);
        build();
    }

    private void build() {
        list.removeAllViews();
        row(R.string.set_theme, themeName(), 0, v -> pickTheme());
        row(R.string.set_font, fontName(), 0, v -> pickFont());
        row(R.string.set_font_size, p.getInt(Prefs.FONT_SIZE, 15) + " sp", 0, v -> pickSize());
        if (p.contains(Prefs.TEXT_COLOR)) {
            row(R.string.set_text_color, "████", p.getInt(Prefs.TEXT_COLOR, Color.BLACK), v -> pickTextColor());
        } else {
            row(R.string.set_text_color, getString(R.string.color_auto), 0, v -> pickTextColor());
        }
        row(R.string.set_background, bgName(), 0, v -> pickBackground());
        row(R.string.set_overlay, p.getInt(Prefs.OVERLAY, 0) + "%", 0, v -> pickOverlay());
        row(R.string.set_language, langName(), 0, v -> pickLanguage());
        toggle(R.string.set_toolbar, Prefs.TOOLBAR, true);
        toggle(R.string.set_wrap, Prefs.WRAP, true);
        toggle(R.string.set_line_numbers, Prefs.LINES, false);
        toggle(R.string.set_highlight, Prefs.HIGHLIGHT, true);
        toggle(R.string.set_auto_indent, Prefs.AUTOINDENT, true);
        row(R.string.set_tab_size, p.getInt(Prefs.TAB_SIZE, 4) + "", 0, v -> pickTabSize());
        row(R.string.set_compiler_url, p.getString(Prefs.COMPILER_URL, Prefs.DEFAULT_COMPILER), 0,
                v -> askText(R.string.set_compiler_url, Prefs.COMPILER_URL, Prefs.DEFAULT_COMPILER, false));
        String tok = p.getString(Prefs.COMPILER_TOKEN, "");
        row(R.string.set_compiler_token, tok.isEmpty() ? getString(R.string.compiler_token_none)
                        : getString(R.string.compiler_token_set), 0,
                v -> askText(R.string.set_compiler_token, Prefs.COMPILER_TOKEN, "", true));
    }

    // ---------- Giao diện sáng/tối, thụt lề, trình biên dịch ----------

    private static final String[] THEME_KEYS = {"system", "light", "dark"};

    private String themeName() {
        String t = p.getString(Prefs.THEME, "system");
        if ("light".equals(t)) return getString(R.string.theme_light);
        if ("dark".equals(t)) return getString(R.string.theme_dark);
        return getString(R.string.theme_system);
    }

    private void pickTheme() {
        String[] items = {getString(R.string.theme_system), getString(R.string.theme_light),
                getString(R.string.theme_dark)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.set_theme)
                .setItems(items, (d, which) -> {
                    p.edit().putString(Prefs.THEME, THEME_KEYS[which]).apply();
                    recreate();
                })
                .show();
    }

    private void pickTextColor() {
        String[] items = {getString(R.string.color_auto), getString(R.string.color_choose)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.set_text_color)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        p.edit().remove(Prefs.TEXT_COLOR).apply();
                        build();
                    } else {
                        colorDialog(R.string.set_text_color, c -> {
                            p.edit().putInt(Prefs.TEXT_COLOR, c).apply();
                            build();
                        });
                    }
                })
                .show();
    }

    private void pickTabSize() {
        final int[] sizes = {2, 3, 4, 6, 8};
        String[] items = new String[sizes.length];
        for (int i = 0; i < sizes.length; i++) items[i] = String.valueOf(sizes[i]);
        new AlertDialog.Builder(this)
                .setTitle(R.string.set_tab_size)
                .setItems(items, (d, which) -> {
                    p.edit().putInt(Prefs.TAB_SIZE, sizes[which]).apply();
                    build();
                })
                .show();
    }

    private void askText(int titleRes, final String key, final String def, boolean secret) {
        final EditText in = new EditText(this);
        in.setSingleLine(true);
        in.setInputType(secret
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        in.setText(p.getString(key, def));
        new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setView(in)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String v = in.getText().toString().trim();
                    if (v.isEmpty()) v = def;
                    if (key.equals(Prefs.COMPILER_URL) && !v.toLowerCase(Locale.ROOT).startsWith("https://")
                            && !v.toLowerCase(Locale.ROOT).startsWith("http://")) {
                        Toast.makeText(this, R.string.compiler_url_invalid, Toast.LENGTH_LONG).show();
                        return;
                    }
                    p.edit().putString(key, v).apply();
                    build();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---------- Dựng giao diện ----------

    private void row(int titleRes, String value, int valueColor, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(dp(16), dp(14), dp(16), dp(14));
        r.setBackgroundResource(android.R.drawable.list_selector_background);

        TextView t = new TextView(this);
        t.setText(titleRes);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        if (valueColor != 0) {
            v.setTextColor(valueColor);
            v.setBackgroundColor(0xFF9E9E9E);
        } else {
            v.setTextColor(0xFF757575);
        }

        r.addView(t);
        r.addView(v);
        r.setOnClickListener(click);
        list.addView(r);
    }

    private void toggle(int titleRes, final String key, boolean def) {
        Switch sw = new Switch(this);
        sw.setText(titleRes);
        sw.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        sw.setPadding(dp(16), dp(14), dp(16), dp(14));
        sw.setChecked(p.getBoolean(key, def));
        sw.setOnCheckedChangeListener((b, checked) -> p.edit().putBoolean(key, checked).apply());
        list.addView(sw);
    }

    // ---------- Font ----------

    private String fontName() {
        String f = p.getString(Prefs.FONT, "monospace");
        for (int i = 0; i < FONT_KEYS.length; i++) {
            if (FONT_KEYS[i].equals(f)) return FONT_NAMES[i];
        }
        return getString(R.string.font_custom);
    }

    private void pickFont() {
        String[] items = new String[FONT_NAMES.length + 1];
        System.arraycopy(FONT_NAMES, 0, items, 0, FONT_NAMES.length);
        items[FONT_NAMES.length] = getString(R.string.font_custom);
        new AlertDialog.Builder(this)
                .setTitle(R.string.set_font)
                .setItems(items, (d, which) -> {
                    if (which < FONT_KEYS.length) {
                        p.edit().putString(Prefs.FONT, FONT_KEYS[which]).apply();
                        build();
                    } else {
                        openPicker("*/*", REQ_FONT);
                    }
                })
                .show();
    }

    private void pickSize() {
        String[] items = new String[SIZES.length];
        for (int i = 0; i < SIZES.length; i++) items[i] = SIZES[i] + " sp";
        new AlertDialog.Builder(this)
                .setTitle(R.string.set_font_size)
                .setItems(items, (d, which) -> {
                    p.edit().putInt(Prefs.FONT_SIZE, SIZES[which]).apply();
                    build();
                })
                .show();
    }

    private void pickOverlay() {
        String[] items = new String[OVERLAYS.length];
        for (int i = 0; i < OVERLAYS.length; i++) items[i] = OVERLAYS[i] + "%";
        new AlertDialog.Builder(this)
                .setTitle(R.string.set_overlay)
                .setItems(items, (d, which) -> {
                    p.edit().putInt(Prefs.OVERLAY, OVERLAYS[which]).apply();
                    build();
                })
                .show();
    }

    // ---------- Hình nền ----------

    private String bgName() {
        String type = p.getString(Prefs.BG_TYPE, "none");
        switch (type) {
            case "color":
                return getString(R.string.bg_color);
            case "image":
                return getString(R.string.bg_image);
            case "video":
                return getString(R.string.bg_video);
            default:
                return getString(R.string.bg_none);
        }
    }

    private void pickBackground() {
        String[] items = {
                getString(R.string.bg_none),
                getString(R.string.bg_color),
                getString(R.string.bg_image_file),
                getString(R.string.bg_image_url),
                getString(R.string.bg_video_file),
                getString(R.string.bg_video_url)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.set_background)
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            p.edit().putString(Prefs.BG_TYPE, "none").apply();
                            build();
                            break;
                        case 1:
                            colorDialog(R.string.bg_color, c -> {
                                p.edit().putString(Prefs.BG_TYPE, "color")
                                        .putString(Prefs.BG_VALUE, String.valueOf(c)).apply();
                                build();
                            });
                            break;
                        case 2:
                            openPicker("image/*", REQ_IMAGE);
                            break;
                        case 3:
                            askUrl(this::downloadImage);
                            break;
                        case 4:
                            openPicker("video/*", REQ_VIDEO);
                            break;
                        default:
                            askUrl(url -> {
                                p.edit().putString(Prefs.BG_TYPE, "video")
                                        .putString(Prefs.BG_VALUE, url).apply();
                                build();
                            });
                            break;
                    }
                })
                .show();
    }

    private void askUrl(final StringCallback cb) {
        final EditText in = new EditText(this);
        in.setHint(R.string.bg_enter_url);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this)
                .setView(in)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String url = in.getText().toString().trim();
                    if (!url.isEmpty()) cb.on(url);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void downloadImage(final String url) {
        new Thread(() -> {
            boolean ok = false;
            final File out = new File(getFilesDir(), "bg_download");
            try {
                if (!url.toLowerCase(Locale.ROOT).startsWith("https://")) throw new Exception("https only");
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(20000);
                InputStream in = c.getInputStream();
                FileOutputStream fo = new FileOutputStream(out);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
                fo.close();
                in.close();
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(out.getPath(), o);
                ok = o.outWidth > 0;
            } catch (Exception ignored) {
                ok = false;
            }
            final boolean success = ok;
            runOnUiThread(() -> {
                if (success) {
                    p.edit().putString(Prefs.BG_TYPE, "image")
                            .putString(Prefs.BG_VALUE, Uri.fromFile(out).toString()).apply();
                    build();
                } else {
                    Toast.makeText(this, R.string.bg_download_failed, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    // ---------- Ngôn ngữ ----------

    private String langName() {
        String tag = p.getString(Prefs.LANG, "");
        if (tag.isEmpty()) return getString(R.string.lang_system);
        Locale l = new Locale(tag);
        return l.getDisplayName(l) + " (" + tag + ")";
    }

    private void pickLanguage() {
        final List<String[]> items = new ArrayList<>();
        for (String code : Locale.getISOLanguages()) {
            Locale l = new Locale(code);
            String name = l.getDisplayName(l);
            if (name == null || name.isEmpty() || name.equals(code)) continue;
            items.add(new String[]{code, name + " (" + code + ")"});
        }
        Collections.sort(items, (a, b) -> a[1].compareToIgnoreCase(b[1]));
        items.add(0, new String[]{"", getString(R.string.lang_system)});

        String[] labels = new String[items.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = items.get(i)[1];
        new AlertDialog.Builder(this)
                .setTitle(R.string.set_language)
                .setItems(labels, (d, which) -> {
                    p.edit().putString(Prefs.LANG, items.get(which)[0]).apply();
                    recreate();
                })
                .show();
    }

    // ---------- Chọn file / màu ----------

    private void openPicker(String mime, int request) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, request);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();

        if (requestCode == REQ_FONT) {
            File dst = new File(getFilesDir(), "custom_font");
            boolean ok = copy(uri, dst);
            if (ok) {
                try {
                    ok = Typeface.createFromFile(dst) != null;
                } catch (Exception e) {
                    ok = false;
                }
            }
            if (ok) {
                p.edit().putString(Prefs.FONT, "custom").apply();
                build();
            } else {
                dst.delete();
                Toast.makeText(this, R.string.font_load_failed, Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQ_IMAGE || requestCode == REQ_VIDEO) {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
                // vẫn dùng được trong phiên hiện tại
            }
            p.edit().putString(Prefs.BG_TYPE, requestCode == REQ_IMAGE ? "image" : "video")
                    .putString(Prefs.BG_VALUE, uri.toString()).apply();
            build();
        }
    }

    private boolean copy(Uri src, File dst) {
        try (InputStream in = getContentResolver().openInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void colorDialog(int titleRes, final IntCallback cb) {
        final AlertDialog[] dlg = new AlertDialog[1];
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(12), dp(12), dp(12), dp(4));
        for (int r = 0; r < 4; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 4; c++) {
                final int color = PALETTE[r * 4 + c];
                View sw = new View(this);
                GradientDrawable g = new GradientDrawable();
                g.setColor(color);
                g.setCornerRadius(dp(8));
                g.setStroke(dp(1), 0xFF9E9E9E);
                sw.setBackground(g);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(56), dp(56));
                lp.setMargins(dp(6), dp(6), dp(6), dp(6));
                sw.setLayoutParams(lp);
                sw.setOnClickListener(v -> {
                    dlg[0].dismiss();
                    cb.on(color);
                });
                row.addView(sw);
            }
            col.addView(row);
        }
        dlg[0] = new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setView(col)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dlg[0].show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
