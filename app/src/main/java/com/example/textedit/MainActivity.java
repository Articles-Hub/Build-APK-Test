package com.example.textedit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Trình soạn thảo văn bản: mở/lưu qua Storage Access Framework, undo/redo, tìm kiếm,
 * thanh công cụ mini, menu mini khi nhấn giữ, chạy HTML/JavaScript, và tùy biến giao diện.
 */
public class MainActivity extends Activity {

    private static final int REQ_OPEN = 1;
    private static final int REQ_SAVE_AS = 2;
    private static final String KEY_URI = "uri";
    private static final String KEY_DIRTY = "dirty";
    private static final int MAX_HISTORY = 60;

    private LineNumberEditText editor;
    private FrameLayout bgRoot;
    private ImageView bgImage;
    private VideoView bgVideo;
    private View searchBar;
    private EditText searchInput;
    private View toolbar;

    private Uri currentUri;
    private boolean dirty;
    private boolean loading;
    private boolean restoring;
    private String langAtCreate = "";
    private float lastX;
    private float lastY;

    private final ArrayList<String> history = new ArrayList<>();
    private int histPos = 0;
    private long lastEditTime = 0;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Prefs.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        langAtCreate = Prefs.get(this).getString(Prefs.LANG, "");
        setContentView(R.layout.activity_main);

        editor = findViewById(R.id.editor);
        bgRoot = findViewById(R.id.bg_root);
        bgImage = findViewById(R.id.bg_image);
        bgVideo = findViewById(R.id.bg_video);
        searchBar = findViewById(R.id.search_bar);
        searchInput = findViewById(R.id.search_input);
        toolbar = findViewById(R.id.toolbar);

        buildToolbar();
        resetHistory("");

        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                if (loading) return;
                if (!restoring) recordHistory(s.toString());
                if (!dirty) {
                    dirty = true;
                    updateTitle();
                }
            }
        });

        // Ghi lại vị trí chạm để đặt menu mini đúng chỗ khi nhấn giữ.
        editor.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                lastX = e.getX();
                lastY = e.getY();
            }
            return false;
        });
        editor.setOnLongClickListener(v -> {
            showMiniPopup();
            return true;
        });

        searchInput.setOnEditorActionListener((v, actionId, ev) -> {
            findNext();
            return true;
        });
        findViewById(R.id.search_next).setOnClickListener(v -> findNext());
        findViewById(R.id.search_close).setOnClickListener(v -> searchBar.setVisibility(View.GONE));

        if (savedInstanceState != null) {
            String u = savedInstanceState.getString(KEY_URI);
            currentUri = u == null ? null : Uri.parse(u);
            dirty = savedInstanceState.getBoolean(KEY_DIRTY);
        } else {
            Intent intent = getIntent();
            String action = intent.getAction();
            if ((Intent.ACTION_VIEW.equals(action) || Intent.ACTION_EDIT.equals(action))
                    && intent.getData() != null) {
                openUri(intent.getData());
            }
        }
        updateTitle();
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        resetHistory(editor.getText().toString());
    }

    @Override
    protected void onResume() {
        super.onResume();
        String lang = Prefs.get(this).getString(Prefs.LANG, "");
        if (!lang.equals(langAtCreate)) {
            recreate();
            return;
        }
        applySettings();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (bgVideo.isPlaying()) bgVideo.pause();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(KEY_URI, currentUri == null ? null : currentUri.toString());
        out.putBoolean(KEY_DIRTY, dirty);
    }

    // ---------- Menu ----------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_new) {
            confirmDiscard(this::newFile);
        } else if (id == R.id.action_open) {
            confirmDiscard(this::pickFile);
        } else if (id == R.id.action_save) {
            save();
        } else if (id == R.id.action_save_as) {
            saveAs();
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (searchBar.getVisibility() == View.VISIBLE) {
            searchBar.setVisibility(View.GONE);
            return;
        }
        confirmDiscard(this::finish);
    }

    // ---------- Áp dụng cài đặt ----------

    private void applySettings() {
        SharedPreferences p = Prefs.get(this);

        editor.setTypeface(loadTypeface(p));
        editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, p.getInt(Prefs.FONT_SIZE, 15));

        int textColor = p.getInt(Prefs.TEXT_COLOR, Color.BLACK);
        editor.setTextColor(textColor);
        boolean lightText = isLight(textColor);
        int base = lightText ? Color.BLACK : Color.WHITE;

        int alpha = Math.max(0, Math.min(100, p.getInt(Prefs.OVERLAY, 0))) * 255 / 100;
        editor.setBackgroundColor(Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base)));
        editor.setGutterColor(lightText ? 0xFF1E1E1E : 0xFFF2F2F2);

        editor.setHorizontallyScrolling(!p.getBoolean(Prefs.WRAP, true));
        editor.setShowLineNumbers(p.getBoolean(Prefs.LINES, false));
        editor.refreshGutter();

        toolbar.setVisibility(p.getBoolean(Prefs.TOOLBAR, true) ? View.VISIBLE : View.GONE);

        // Hình nền
        String type = p.getString(Prefs.BG_TYPE, "none");
        String value = p.getString(Prefs.BG_VALUE, "");
        bgImage.setVisibility(View.GONE);
        bgImage.setImageDrawable(null);
        if (bgVideo.isPlaying()) bgVideo.stopPlayback();
        bgVideo.setVisibility(View.GONE);
        int rootColor = base;
        if ("color".equals(type)) {
            try {
                rootColor = Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                // giữ màu mặc định
            }
        } else if ("image".equals(type) && !value.isEmpty()) {
            bgImage.setVisibility(View.VISIBLE);
            showBgImage(value);
        } else if ("video".equals(type) && !value.isEmpty()) {
            bgVideo.setVisibility(View.VISIBLE);
            playBgVideo(value);
        }
        bgRoot.setBackgroundColor(rootColor);
    }

    private Typeface loadTypeface(SharedPreferences p) {
        String f = p.getString(Prefs.FONT, "monospace");
        switch (f) {
            case "sans":
                return Typeface.SANS_SERIF;
            case "serif":
                return Typeface.SERIF;
            case "casual":
                return Typeface.create("casual", Typeface.NORMAL);
            case "cursive":
                return Typeface.create("cursive", Typeface.NORMAL);
            case "custom":
                try {
                    File file = new File(getFilesDir(), "custom_font");
                    if (file.exists()) return Typeface.createFromFile(file);
                } catch (Exception ignored) {
                    // rơi về monospace
                }
                return Typeface.MONOSPACE;
            default:
                return Typeface.MONOSPACE;
        }
    }

    private static boolean isLight(int c) {
        double lum = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0;
        return lum > 0.5;
    }

    private void showBgImage(final String uriStr) {
        final DisplayMetrics dm = getResources().getDisplayMetrics();
        new Thread(() -> {
            final Bitmap bmp = decodeSampled(Uri.parse(uriStr), dm.widthPixels, dm.heightPixels);
            runOnUiThread(() -> {
                if (bmp != null) bgImage.setImageBitmap(bmp);
            });
        }).start();
    }

    private Bitmap decodeSampled(Uri uri, int w, int h) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream in = getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(in, null, o);
            in.close();
            int sample = 1;
            while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            in = getContentResolver().openInputStream(uri);
            Bitmap b = BitmapFactory.decodeStream(in, null, o);
            in.close();
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    private void playBgVideo(String uriStr) {
        try {
            bgVideo.setVideoURI(Uri.parse(uriStr));
            bgVideo.setOnPreparedListener(mp -> {
                mp.setLooping(true);
                mp.setVolume(0f, 0f);
            });
            bgVideo.setOnErrorListener((mp, what, extra) -> true);
            bgVideo.start();
        } catch (Exception ignored) {
            bgVideo.setVisibility(View.GONE);
        }
    }

    // ---------- Thanh công cụ mini ----------

    private void buildToolbar() {
        LinearLayout row = findViewById(R.id.toolbar_row);
        addToolbarButton(row, R.string.action_open, () -> confirmDiscard(this::pickFile));
        addToolbarButton(row, R.string.tb_paste, this::pasteText);
        addToolbarButton(row, R.string.tb_copy, this::copyText);
        addToolbarButton(row, R.string.tb_undo, this::undo);
        addToolbarButton(row, R.string.tb_redo, this::redo);
        addToolbarButton(row, R.string.tb_search, this::toggleSearch);
        addToolbarButton(row, R.string.action_save, this::save);
        addToolbarButton(row, R.string.tb_run, this::runFile);
        addToolbarButton(row, R.string.tb_close, () -> confirmDiscard(this::newFile));
    }

    private void addToolbarButton(LinearLayout row, int labelRes, final Runnable action) {
        Button b = new Button(this, null, android.R.attr.borderlessButtonStyle);
        b.setText(labelRes);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setOnClickListener(v -> action.run());
        row.addView(b);
    }

    // ---------- Clipboard ----------

    private void pasteText() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip() == null
                || cm.getPrimaryClip().getItemCount() == 0) return;
        CharSequence s = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
        if (s == null) return;
        int a = editor.getSelectionStart();
        int b = editor.getSelectionEnd();
        if (a < 0 || b < 0) {
            a = editor.length();
            b = a;
        }
        editor.getText().replace(Math.min(a, b), Math.max(a, b), s);
    }

    /** Sao chép phần đang chọn; nếu chưa chọn gì thì sao chép toàn bộ văn bản. */
    private void copyText() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm == null) return;
        int a = Math.min(editor.getSelectionStart(), editor.getSelectionEnd());
        int b = Math.max(editor.getSelectionStart(), editor.getSelectionEnd());
        CharSequence text = (a < 0 || a == b) ? editor.getText() : editor.getText().subSequence(a, b);
        cm.setPrimaryClip(ClipData.newPlainText("text", text.toString()));
    }

    private void cutText() {
        int a = Math.min(editor.getSelectionStart(), editor.getSelectionEnd());
        int b = Math.max(editor.getSelectionStart(), editor.getSelectionEnd());
        if (a < 0 || a == b) return;
        copyText();
        editor.getText().delete(a, b);
    }

    // ---------- Undo / redo ----------

    private void resetHistory(String text) {
        history.clear();
        history.add(text);
        histPos = 0;
        lastEditTime = 0;
    }

    private void recordHistory(String now) {
        while (history.size() > histPos + 1) history.remove(history.size() - 1);
        long t = SystemClock.uptimeMillis();
        if (histPos > 0 && t - lastEditTime < 700) {
            history.set(histPos, now); // gộp các lần gõ liên tiếp thành một bước
        } else {
            history.add(now);
            histPos++;
            if (history.size() > MAX_HISTORY) {
                history.remove(0);
                histPos--;
            }
        }
        lastEditTime = t;
    }

    private void undo() {
        if (histPos > 0) {
            histPos--;
            restoreText(history.get(histPos));
        }
    }

    private void redo() {
        if (histPos < history.size() - 1) {
            histPos++;
            restoreText(history.get(histPos));
        }
    }

    private void restoreText(String t) {
        restoring = true;
        int cur = editor.getSelectionStart();
        editor.setText(t);
        int pos = cur < 0 ? t.length() : Math.min(cur, t.length());
        editor.setSelection(pos);
        restoring = false;
        lastEditTime = 0;
    }

    // ---------- Tìm kiếm ----------

    private void toggleSearch() {
        if (searchBar.getVisibility() == View.VISIBLE) {
            searchBar.setVisibility(View.GONE);
        } else {
            searchBar.setVisibility(View.VISIBLE);
            searchInput.requestFocus();
        }
    }

    private void findNext() {
        String q = searchInput.getText().toString();
        if (q.isEmpty()) return;
        String text = editor.getText().toString();
        int from = Math.max(0, editor.getSelectionEnd());
        int idx = indexOfIgnoreCase(text, q, from);
        if (idx < 0) idx = indexOfIgnoreCase(text, q, 0);
        if (idx < 0) {
            Toast.makeText(this, R.string.search_not_found, Toast.LENGTH_SHORT).show();
            return;
        }
        editor.requestFocus();
        editor.setSelection(idx, idx + q.length());
    }

    private static int indexOfIgnoreCase(String text, String q, int from) {
        int last = text.length() - q.length();
        for (int i = from; i <= last; i++) {
            if (text.regionMatches(true, i, q, 0, q.length())) return i;
        }
        return -1;
    }

    // ---------- Chạy (HTML / JavaScript) ----------

    private void runFile() {
        String name = currentName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html") || name.endsWith(".htm")) {
            runHtml();
        } else if (name.endsWith(".js")) {
            runJs();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.run_as)
                    .setItems(new String[]{"HTML", "JavaScript"}, (d, which) -> {
                        if (which == 0) runHtml();
                        else runJs();
                    })
                    .show();
        }
    }

    private void runHtml() {
        WebView wv = new WebView(this);
        wv.getSettings().setJavaScriptEnabled(true);
        wv.getSettings().setAllowFileAccess(false);
        wv.loadDataWithBaseURL(null, editor.getText().toString(), "text/html", "UTF-8", null);
        FrameLayout box = new FrameLayout(this);
        int h = (int) (getResources().getDisplayMetrics().heightPixels * 0.65f);
        box.addView(wv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        new AlertDialog.Builder(this)
                .setView(box)
                .setPositiveButton(R.string.close, null)
                .setOnDismissListener(d -> wv.destroy())
                .show();
    }

    private void runJs() {
        final StringBuilder out = new StringBuilder();
        final WebView wv = new WebView(this);
        wv.getSettings().setJavaScriptEnabled(true);
        wv.getSettings().setAllowFileAccess(false);
        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage m) {
                out.append(m.message());
                if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    out.append("  (line ").append(m.lineNumber() - 1).append(")");
                }
                out.append('\n');
                return true;
            }
        });
        String html = "<html><body><script>\n" + editor.getText() + "\n</script></body></html>";
        wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
        // Đợi một chút để script chạy xong rồi hiện kết quả console.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            String result = out.length() == 0 ? getString(R.string.run_no_output) : out.toString();
            wv.destroy();
            new AlertDialog.Builder(this)
                    .setTitle(R.string.run_output)
                    .setMessage(result)
                    .setPositiveButton(R.string.close, null)
                    .show();
        }, 1500);
    }

    // ---------- Menu mini khi nhấn giữ ----------

    private void showMiniPopup() {
        editor.requestFocus();
        boolean hasSelection = editor.getSelectionStart() != editor.getSelectionEnd();
        if (!hasSelection) {
            int off = editor.getOffsetForPosition(lastX, lastY);
            if (off >= 0) editor.setSelection(off);
        }

        String text = editor.getText().toString();
        int a = Math.max(0, Math.min(editor.getSelectionStart(), editor.getSelectionEnd()));
        int b = Math.max(0, Math.max(editor.getSelectionStart(), editor.getSelectionEnd()));
        int ls = text.lastIndexOf('\n', a - 1) + 1;
        int le = text.indexOf('\n', b);
        if (le < 0) le = text.length();
        // Chỉ hiện thụt lề khi dòng đang chạm có chữ.
        boolean nearText = !text.substring(ls, le).trim().isEmpty();

        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        boolean canPaste = cm != null && cm.hasPrimaryClip();

        final PopupWindow[] pw = new PopupWindow[1];
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF0303030);
        bg.setCornerRadius(dp(8));
        box.setBackground(bg);
        box.setPadding(dp(4), dp(2), dp(4), dp(2));

        if (canPaste) addPopupItem(box, pw, R.string.tb_paste, this::pasteText);
        addPopupItem(box, pw, R.string.pp_select_all, () -> editor.selectAll());
        if (hasSelection) {
            addPopupItem(box, pw, R.string.tb_copy, this::copyText);
            addPopupItem(box, pw, R.string.pp_cut, this::cutText);
        }
        if (nearText) {
            addPopupItem(box, pw, R.string.pp_indent, () -> shiftIndent(true));
            addPopupItem(box, pw, R.string.pp_outdent, () -> shiftIndent(false));
        }

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(box);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        box.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int width = Math.min(box.getMeasuredWidth(), dm.widthPixels - dp(16));
        int height = box.getMeasuredHeight();

        pw[0] = new PopupWindow(hs, width, height, true);
        pw[0].setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        pw[0].setOutsideTouchable(true);

        int[] loc = new int[2];
        editor.getLocationOnScreen(loc);
        int x = loc[0] + (int) lastX - width / 2;
        x = Math.max(dp(8), Math.min(x, dm.widthPixels - width - dp(8)));
        int y = loc[1] + (int) lastY - height - dp(28);
        if (y < dp(8)) y = loc[1] + (int) lastY + dp(28);
        pw[0].showAtLocation(editor, Gravity.NO_GRAVITY, x, y);
    }

    private void addPopupItem(LinearLayout box, final PopupWindow[] pw, int labelRes, final Runnable action) {
        TextView tv = new TextView(this);
        tv.setText(labelRes);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setPadding(dp(12), dp(10), dp(12), dp(10));
        tv.setOnClickListener(v -> {
            if (pw[0] != null) pw[0].dismiss();
            action.run();
        });
        box.addView(tv);
    }

    /** Tăng hoặc giảm thụt lề (4 khoảng trắng) cho các dòng đang chọn. */
    private void shiftIndent(boolean indent) {
        String text = editor.getText().toString();
        int selStart = editor.getSelectionStart();
        int selEnd = editor.getSelectionEnd();
        int a = Math.max(0, Math.min(selStart, selEnd));
        int b = Math.max(0, Math.max(selStart, selEnd));
        boolean hadSelection = a != b;

        int ls = text.lastIndexOf('\n', a - 1) + 1;
        int le = b;
        if (b > a && text.charAt(b - 1) == '\n') le = b - 1;
        int end = text.indexOf('\n', le);
        if (end < 0) end = text.length();

        String[] lines = text.substring(ls, end).split("\n", -1);
        StringBuilder sb = new StringBuilder();
        int firstDelta = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String result;
            if (indent) {
                result = line.isEmpty() ? line : "    " + line;
            } else {
                int k = 0;
                while (k < 4 && k < line.length() && line.charAt(k) == ' ') k++;
                if (k == 0 && line.startsWith("\t")) k = 1;
                result = line.substring(k);
            }
            if (i == 0) firstDelta = result.length() - line.length();
            if (i > 0) sb.append('\n');
            sb.append(result);
        }
        editor.getText().replace(ls, end, sb);
        if (hadSelection) {
            editor.setSelection(ls, ls + sb.length());
        } else {
            editor.setSelection(Math.max(ls, a + firstDelta));
        }
    }

    // ---------- Thao tác file ----------

    private void newFile() {
        loading = true;
        editor.setText("");
        loading = false;
        currentUri = null;
        dirty = false;
        resetHistory("");
        updateTitle();
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_OPEN);
    }

    private void saveAs() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TITLE, "untitled.txt");
        startActivityForResult(i, REQ_SAVE_AS);
    }

    private void save() {
        if (currentUri == null) {
            saveAs();
        } else {
            writeUri(currentUri);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_OPEN) {
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception ignored) {
                // Một số provider không hỗ trợ quyền lâu dài.
            }
            openUri(uri);
        } else if (requestCode == REQ_SAVE_AS) {
            writeUri(uri);
        }
    }

    private void openUri(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) throw new java.io.IOException("null stream");
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try {
                byte[] chunk = new byte[8192];
                int n;
                while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
            } finally {
                in.close();
            }
            String text = new String(buf.toByteArray(), StandardCharsets.UTF_8);
            loading = true;
            editor.setText(text);
            loading = false;
            currentUri = uri;
            dirty = false;
            resetHistory(text);
            updateTitle();
        } catch (Exception e) {
            Toast.makeText(this, R.string.open_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void writeUri(Uri uri) {
        try {
            // "wt" ghi đè và cắt phần cũ, tránh thừa đuôi khi nội dung ngắn lại.
            OutputStream out = getContentResolver().openOutputStream(uri, "wt");
            if (out == null) throw new java.io.IOException("null stream");
            try {
                out.write(editor.getText().toString().getBytes(StandardCharsets.UTF_8));
            } finally {
                out.close();
            }
            currentUri = uri;
            dirty = false;
            updateTitle();
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show();
        }
    }

    // ---------- Tiện ích ----------

    private void confirmDiscard(final Runnable action) {
        if (!dirty) {
            action.run();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.unsaved_title)
                .setMessage(R.string.unsaved_message)
                .setPositiveButton(R.string.discard, (d, w) -> {
                    dirty = false;
                    action.run();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String currentName() {
        if (currentUri == null) return "";
        String name = queryName(currentUri);
        return name == null ? "" : name;
    }

    private void updateTitle() {
        String name = currentName();
        if (name.isEmpty()) name = getString(R.string.untitled);
        setTitle((dirty ? "* " : "") + name);
    }

    private String queryName(Uri uri) {
        ContentResolver cr = getContentResolver();
        Cursor c = null;
        try {
            c = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
            // rơi về đoạn cuối của đường dẫn
        } finally {
            if (c != null) c.close();
        }
        return uri.getLastPathSegment();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
