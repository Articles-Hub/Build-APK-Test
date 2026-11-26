package com.example.textedit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Trình soạn thảo văn bản nhiều thẻ: hoàn tác không giới hạn, tìm/thay thế, nhiều bảng mã,
 * tô màu cú pháp, xem trước, chạy mã, kết nối máy chủ/Git/root.
 */
public class MainActivity extends Activity {

    private static final int REQ_OPEN = 1;
    private static final int REQ_SAVE_AS = 2;
    private static final int MAX_FILE = 12 * 1024 * 1024;
    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{6}\\b|#[0-9a-fA-F]{3}\\b");

    /** Các tài liệu sống sót qua recreate() khi đổi giao diện hoặc ngôn ngữ. */
    private static final ArrayList<Doc> sDocs = new ArrayList<>();
    private static int sCur = 0;

    private LineNumberEditText editor;
    private FrameLayout bgRoot;
    private ImageView bgImage;
    private VideoView bgVideo;
    private HorizontalScrollView tabScroll;
    private LinearLayout tabRow;
    private View searchPanel;
    private EditText findInput;
    private EditText replaceInput;
    private ToggleButton tgCase;
    private ToggleButton tgRegex;
    private View toolbar;
    private View statusBar;
    private TextView statusPos;
    private TextView statusLang;
    private TextView statusEnc;
    private HighlightController hl;

    private boolean dark;
    private String cfgAtCreate = "";
    private boolean loading;
    private boolean restoring;
    private boolean programmatic;
    private boolean lastDirty;
    private boolean autoIndent = true;
    private int tabSize = 4;
    private String hlKey = "";
    private float lastX;
    private float lastY;
    private String lastStdin = "";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Prefs.wrap(base));
    }

    // =====================================================================
    // Vòng đời
    // =====================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Prefs.applyTheme(this);
        super.onCreate(savedInstanceState);
        cfgAtCreate = Prefs.configKey(this);
        dark = Prefs.isDark(this);
        setContentView(R.layout.activity_main);

        editor = (LineNumberEditText) findViewById(R.id.editor);
        bgRoot = (FrameLayout) findViewById(R.id.bg_root);
        bgImage = (ImageView) findViewById(R.id.bg_image);
        bgVideo = (VideoView) findViewById(R.id.bg_video);
        tabScroll = (HorizontalScrollView) findViewById(R.id.tab_scroll);
        tabRow = (LinearLayout) findViewById(R.id.tab_row);
        searchPanel = findViewById(R.id.search_panel);
        findInput = (EditText) findViewById(R.id.find_input);
        replaceInput = (EditText) findViewById(R.id.replace_input);
        tgCase = (ToggleButton) findViewById(R.id.tg_case);
        tgRegex = (ToggleButton) findViewById(R.id.tg_regex);
        toolbar = findViewById(R.id.toolbar);
        statusBar = findViewById(R.id.status_bar);
        statusPos = (TextView) findViewById(R.id.status_pos);
        statusLang = (TextView) findViewById(R.id.status_lang);
        statusEnc = (TextView) findViewById(R.id.status_enc);

        hl = new HighlightController(editor);
        editor.setListener(new LineNumberEditText.Listener() {
            @Override
            public void onScroll() {
                hl.onScroll();
            }

            @Override
            public void onSelection(int selStart, int selEnd) {
                updateStatus();
            }
        });
        editor.setFilters(new InputFilter[]{autoIndentFilter});
        editor.addTextChangedListener(watcher);

        editor.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    lastX = e.getX();
                    lastY = e.getY();
                }
                return false;
            }
        });
        editor.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showMiniPopup();
                return true;
            }
        });

        buildToolbar();
        setupSearchPanel();
        statusPos.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gotoLineDialog();
            }
        });
        statusLang.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseLanguage();
            }
        });
        statusEnc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEncodingDialog();
            }
        });

        if (sDocs.isEmpty()) {
            sDocs.add(new Doc());
            sCur = 0;
        }
        sCur = Math.max(0, Math.min(sCur, sDocs.size() - 1));
        showDoc(cur());
        if (savedInstanceState == null) handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if ((Intent.ACTION_VIEW.equals(action) || Intent.ACTION_EDIT.equals(action)) && intent.getData() != null) {
            openBackend(new Backends.Saf(this, intent.getData()));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!cfgAtCreate.equals(Prefs.configKey(this))) {
            stashCurrent();
            recreate();
            return;
        }
        applySettings();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!cfgAtCreate.equals(Prefs.configKey(this))) {
            stashCurrent();
            recreate();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stashCurrent();
        if (bgVideo.isPlaying()) bgVideo.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            sDocs.clear();
            sCur = 0;
        }
    }

    // =====================================================================
    // Menu
    // =====================================================================

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_new) {
            newTab();
        } else if (id == R.id.action_open) {
            pickFile();
        } else if (id == R.id.action_save) {
            save();
        } else if (id == R.id.action_save_as) {
            saveAs();
        } else if (id == R.id.action_open_remote) {
            openRemote();
        } else if (id == R.id.action_open_root) {
            openRoot();
        } else if (id == R.id.action_find) {
            showSearch(false);
        } else if (id == R.id.action_goto) {
            gotoLineDialog();
        } else if (id == R.id.action_encoding) {
            showEncodingDialog();
        } else if (id == R.id.action_color) {
            pickColor();
        } else if (id == R.id.action_preview) {
            preview();
        } else if (id == R.id.action_run_online) {
            runOnline();
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (searchPanel.getVisibility() == View.VISIBLE) {
            searchPanel.setVisibility(View.GONE);
            return;
        }
        int dirtyCount = 0;
        for (Doc d : sDocs) if (d.dirty()) dirtyCount++;
        if (dirtyCount == 0) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.unsaved_title)
                .setMessage(getString(R.string.unsaved_exit, dirtyCount))
                .setPositiveButton(R.string.discard, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        finish();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // =====================================================================
    // Bàn phím vật lý
    // =====================================================================

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            int k = e.getKeyCode();
            boolean shift = e.isShiftPressed();
            boolean inEditor = getCurrentFocus() == editor;
            if (e.isCtrlPressed()) {
                switch (k) {
                    case KeyEvent.KEYCODE_S:
                        if (shift) saveAs();
                        else save();
                        return true;
                    case KeyEvent.KEYCODE_O:
                        pickFile();
                        return true;
                    case KeyEvent.KEYCODE_N:
                        newTab();
                        return true;
                    case KeyEvent.KEYCODE_W:
                        closeTab(sCur);
                        return true;
                    case KeyEvent.KEYCODE_F:
                        showSearch(false);
                        return true;
                    case KeyEvent.KEYCODE_H:
                        showSearch(true);
                        return true;
                    case KeyEvent.KEYCODE_G:
                        gotoLineDialog();
                        return true;
                    case KeyEvent.KEYCODE_Z:
                        if (inEditor) {
                            if (shift) redo();
                            else undo();
                            return true;
                        }
                        break;
                    case KeyEvent.KEYCODE_Y:
                        if (inEditor) {
                            redo();
                            return true;
                        }
                        break;
                    case KeyEvent.KEYCODE_TAB:
                    case KeyEvent.KEYCODE_PAGE_DOWN:
                        switchTo((sCur + (shift ? sDocs.size() - 1 : 1)) % sDocs.size());
                        return true;
                    case KeyEvent.KEYCODE_PAGE_UP:
                        switchTo((sCur + sDocs.size() - 1) % sDocs.size());
                        return true;
                    default:
                        break;
                }
            } else if (k == KeyEvent.KEYCODE_TAB && inEditor) {
                tabKey(shift);
                return true;
            } else if (k == KeyEvent.KEYCODE_F3) {
                findNext(!shift);
                return true;
            } else if (k == KeyEvent.KEYCODE_ESCAPE && searchPanel.getVisibility() == View.VISIBLE) {
                searchPanel.setVisibility(View.GONE);
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    private void tabKey(boolean shift) {
        int a = Math.max(0, Math.min(editor.getSelectionStart(), editor.getSelectionEnd()));
        int b = Math.max(0, Math.max(editor.getSelectionStart(), editor.getSelectionEnd()));
        String text = editor.getText().toString();
        boolean multi = text.substring(a, b).indexOf('\n') >= 0;
        if (shift || multi) {
            shiftIndent(!shift);
            return;
        }
        int ls = text.lastIndexOf('\n', a - 1) + 1;
        int n = tabSize - ((a - ls) % tabSize);
        edit(a, b, spaces(n));
    }

    // =====================================================================
    // Tài liệu và thẻ
    // =====================================================================

    private Doc cur() {
        return sDocs.get(sCur);
    }

    private String textOf(Doc d) {
        return d == cur() ? editor.getText().toString() : d.text;
    }

    private void stashCurrent() {
        if (sDocs.isEmpty() || editor == null) return;
        Doc d = cur();
        d.text = editor.getText().toString();
        d.selStart = editor.getSelectionStart();
        d.selEnd = editor.getSelectionEnd();
        d.scrollY = editor.getScrollY();
    }

    /** Đưa nội dung của tài liệu vào ô soạn thảo. */
    private void showDoc(final Doc d) {
        loading = true;
        editor.setText(d.text);
        loading = false;
        int len = editor.length();
        editor.setSelection(Math.max(0, Math.min(d.selStart, len)), Math.max(0, Math.min(d.selEnd, len)));
        final int sy = d.scrollY;
        if (sy > 0) {
            editor.post(new Runnable() {
                @Override
                public void run() {
                    editor.scrollTo(editor.getScrollX(), sy);
                }
            });
        }
        configureHighlight(true);
        lastDirty = d.dirty();
        updateTitle();
        updateStatus();
        renderTabs();
    }

    private void newTab() {
        Doc d = new Doc();
        addDoc(d);
    }

    private void addDoc(Doc d) {
        // Thay thế thẻ trống chưa sửa thay vì thêm thẻ mới.
        Doc c = cur();
        if (sDocs.size() == 1 && c.backend == null && !c.dirty() && textOf(c).isEmpty() && !d.text.isEmpty()) {
            sDocs.set(0, d);
            sCur = 0;
            showDoc(d);
            return;
        }
        stashCurrent();
        sDocs.add(d);
        sCur = sDocs.size() - 1;
        showDoc(d);
    }

    private void switchTo(int i) {
        if (i == sCur || i < 0 || i >= sDocs.size()) return;
        stashCurrent();
        sCur = i;
        showDoc(cur());
    }

    private void closeTab(final int idx) {
        final Doc d = sDocs.get(idx);
        confirmDiscard(d, new Runnable() {
            @Override
            public void run() {
                removeTab(idx);
            }
        });
    }

    private void removeTab(int idx) {
        boolean wasCur = idx == sCur;
        sDocs.remove(idx);
        if (sDocs.isEmpty()) {
            sDocs.add(new Doc());
            sCur = 0;
            showDoc(cur());
            return;
        }
        if (idx < sCur) {
            sCur--;
        } else if (wasCur) {
            sCur = Math.min(sCur, sDocs.size() - 1);
            showDoc(cur());
            return;
        }
        renderTabs();
    }

    private void confirmDiscard(Doc d, final Runnable action) {
        if (!d.dirty()) {
            action.run();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.unsaved_title)
                .setMessage(R.string.unsaved_message)
                .setPositiveButton(R.string.discard, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface di, int w) {
                        action.run();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void renderTabs() {
        tabRow.removeAllViews();
        int fg = dark ? 0xFFE0E0E0 : 0xFF212121;
        int selBg = dark ? 0xFF3C3F41 : 0xFFDDDDDD;
        tabScroll.setBackgroundColor(dark ? 0xFF2B2B2B : 0xFFF2F2F2);
        View selected = null;
        for (int i = 0; i < sDocs.size(); i++) {
            final int idx = i;
            Doc d = sDocs.get(i);
            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.HORIZONTAL);
            tab.setGravity(Gravity.CENTER_VERTICAL);
            if (i == sCur) {
                tab.setBackgroundColor(selBg);
                selected = tab;
            }
            TextView t = new TextView(this);
            t.setText((d.dirty() ? "● " : "") + d.title(getString(R.string.untitled)));
            t.setTextColor(fg);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setMaxWidth(dp(150));
            t.setPadding(dp(12), dp(10), dp(4), dp(10));
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchTo(idx);
                }
            });
            TextView x = new TextView(this);
            x.setText("✕");
            x.setTextColor(fg);
            x.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            x.setPadding(dp(6), dp(10), dp(12), dp(10));
            x.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeTab(idx);
                }
            });
            tab.addView(t);
            tab.addView(x);
            tabRow.addView(tab);
        }
        TextView plus = new TextView(this);
        plus.setText("＋");
        plus.setTextColor(fg);
        plus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        plus.setPadding(dp(14), dp(6), dp(14), dp(6));
        plus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                newTab();
            }
        });
        tabRow.addView(plus);
        final View sel = selected;
        if (sel != null) {
            tabScroll.post(new Runnable() {
                @Override
                public void run() {
                    tabScroll.smoothScrollTo(Math.max(0, sel.getLeft() - dp(40)), 0);
                }
            });
        }
    }

    private void refreshDirty() {
        boolean d = cur().dirty();
        if (d != lastDirty) {
            lastDirty = d;
            renderTabs();
            updateTitle();
        }
    }

    private void updateTitle() {
        Doc d = cur();
        setTitle((d.dirty() ? "* " : "") + d.title(getString(R.string.untitled)));
    }

    private void updateStatus() {
        if (statusPos == null || sDocs.isEmpty()) return;
        int sel = Math.max(0, editor.getSelectionStart());
        int line = editor.lineOfOffset(sel);
        int col = sel - editor.offsetOfLine(line) + 1;
        statusPos.setText(getString(R.string.status_pos, line + 1, editor.totalLines(), col));
        Lang l = cur().lang();
        statusLang.setText(l == null ? getString(R.string.plain_text) : l.name);
        statusEnc.setText(cur().encodingLabel());
    }

    // =====================================================================
    // Theo dõi thay đổi và hoàn tác không giới hạn
    // =====================================================================

    private final TextWatcher watcher = new TextWatcher() {
        private int pStart;
        private String pOld = "";
        private String pNew = "";

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            if (loading || restoring) return;
            pStart = start;
            pOld = count == 0 ? "" : s.subSequence(start, start + count).toString();
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (loading || restoring) return;
            pNew = count == 0 ? "" : s.subSequence(start, start + count).toString();
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (loading || restoring) return;
            cur().undo.record(pStart, pOld, pNew, SystemClock.uptimeMillis());
            refreshDirty();
        }
    };

    private void undo() {
        UndoStack.Op op = cur().undo.undo();
        if (op != null) applyOp(op.start, op.newText.length(), op.oldText);
    }

    private void redo() {
        UndoStack.Op op = cur().undo.redo();
        if (op != null) applyOp(op.start, op.oldText.length(), op.newText);
    }

    private void applyOp(int start, int removeLen, String insert) {
        Editable e = editor.getText();
        if (start < 0 || start + removeLen > e.length()) return;
        restoring = true;
        try {
            e.replace(start, start + removeLen, insert);
            editor.setSelection(start + insert.length());
        } finally {
            restoring = false;
        }
        scrollIntoView(start + insert.length());
        refreshDirty();
    }

    /** Sửa văn bản bằng mã (vẫn được ghi vào lịch sử hoàn tác, nhưng không tự thụt lề). */
    private void edit(int a, int b, CharSequence replacement) {
        programmatic = true;
        try {
            editor.getText().replace(Math.min(a, b), Math.max(a, b), replacement);
        } finally {
            programmatic = false;
        }
    }

    // =====================================================================
    // Tự động thụt lề và định dạng
    // =====================================================================

    private final InputFilter autoIndentFilter = new InputFilter() {
        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            if (!autoIndent || loading || restoring || programmatic) return null;
            if (end - start != 1 || source.charAt(start) != '\n') return null;
            int ls = dstart;
            while (ls > 0 && dest.charAt(ls - 1) != '\n') ls--;
            int k = ls;
            while (k < dstart && (dest.charAt(k) == ' ' || dest.charAt(k) == '\t')) k++;
            StringBuilder indent = new StringBuilder("\n");
            indent.append(dest.subSequence(ls, k));
            int p = dstart - 1;
            while (p >= ls && (dest.charAt(p) == ' ' || dest.charAt(p) == '\t')) p--;
            if (p >= ls) {
                char last = dest.charAt(p);
                boolean extra = last == '{' || last == '(' || last == '[';
                if (last == ':') {
                    Lang l = cur().lang();
                    String n = l == null ? "" : l.name;
                    extra = n.equals("Python") || n.equals("YAML") || n.equals("Nim") || n.equals("CoffeeScript")
                            || n.equals("Cython") || n.equals("Elm");
                }
                if (extra) indent.append(spaces(tabSize));
            }
            return indent.toString();
        }
    };

    private static String spaces(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(' ');
        return sb.toString();
    }

    /** Tăng hoặc giảm thụt lề cho các dòng đang chọn. */
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

        String pad = spaces(tabSize);
        String[] lines = text.substring(ls, end).split("\n", -1);
        StringBuilder sb = new StringBuilder();
        int firstDelta = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String result;
            if (indent) {
                result = line.isEmpty() ? line : pad + line;
            } else {
                int k = 0;
                while (k < tabSize && k < line.length() && line.charAt(k) == ' ') k++;
                if (k == 0 && line.startsWith("\t")) k = 1;
                result = line.substring(k);
            }
            if (i == 0) firstDelta = result.length() - line.length();
            if (i > 0) sb.append('\n');
            sb.append(result);
        }
        edit(ls, end, sb);
        if (hadSelection) {
            editor.setSelection(ls, ls + sb.length());
        } else {
            editor.setSelection(Math.max(ls, a + firstDelta));
        }
    }

    // =====================================================================
    // Áp dụng cài đặt
    // =====================================================================

    private void applySettings() {
        SharedPreferences p = Prefs.get(this);

        editor.setTypeface(loadTypeface(p));
        editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, p.getInt(Prefs.FONT_SIZE, 15));

        int textColor = p.getInt(Prefs.TEXT_COLOR, Prefs.defaultTextColor(this));
        editor.setTextColor(textColor);
        boolean lightText = HighlightController.isLight(textColor);
        int base = lightText ? Color.BLACK : Color.WHITE;

        int alpha = Math.max(0, Math.min(100, p.getInt(Prefs.OVERLAY, 0))) * 255 / 100;
        editor.setBackgroundColor(Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base)));
        editor.setGutterColor(lightText ? 0xFF1E1E1E : 0xFFF2F2F2);

        editor.setHorizontallyScrolling(!p.getBoolean(Prefs.WRAP, true));
        editor.setShowLineNumbers(p.getBoolean(Prefs.LINES, false));
        editor.refreshGutter();

        autoIndent = p.getBoolean(Prefs.AUTOINDENT, true);
        tabSize = Math.max(1, p.getInt(Prefs.TAB_SIZE, 4));

        // Màu các thanh theo giao diện sáng/tối.
        int barBg = dark ? 0xFF2B2B2B : 0xFFF2F2F2;
        int barFg = dark ? 0xFFE0E0E0 : 0xFF212121;
        toolbar.setBackgroundColor(barBg);
        statusBar.setBackgroundColor(barBg);
        searchPanel.setBackgroundColor(barBg);
        statusPos.setTextColor(barFg);
        statusLang.setTextColor(barFg);
        statusEnc.setTextColor(barFg);
        tintToolbar(barFg);
        toolbar.setVisibility(p.getBoolean(Prefs.TOOLBAR, true) ? View.VISIBLE : View.GONE);

        configureHighlight(false);
        renderTabs();

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

    /** Cấu hình lại bộ tô màu khi đổi tài liệu, ngôn ngữ hoặc cài đặt. */
    private void configureHighlight(boolean force) {
        SharedPreferences p = Prefs.get(this);
        boolean on = p.getBoolean(Prefs.HIGHLIGHT, true);
        int textColor = p.getInt(Prefs.TEXT_COLOR, Prefs.defaultTextColor(this));
        boolean darkPalette = HighlightController.isLight(textColor);
        Lang l = cur().lang();
        String key = (l == null ? "-" : l.name) + "|" + on + "|" + darkPalette + "|" + sCur;
        if (!force && key.equals(hlKey)) return;
        hlKey = key;
        hl.configure(l, on, darkPalette);
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

    private void showBgImage(final String uriStr) {
        final DisplayMetrics dm = getResources().getDisplayMetrics();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap bmp = decodeSampled(Uri.parse(uriStr), dm.widthPixels, dm.heightPixels);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (bmp != null) bgImage.setImageBitmap(bmp);
                    }
                });
            }
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
            bgVideo.setOnPreparedListener(new android.media.MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(android.media.MediaPlayer mp) {
                    mp.setLooping(true);
                    mp.setVolume(0f, 0f);
                }
            });
            bgVideo.setOnErrorListener(new android.media.MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(android.media.MediaPlayer mp, int what, int extra) {
                    return true;
                }
            });
            bgVideo.start();
        } catch (Exception ignored) {
            bgVideo.setVisibility(View.GONE);
        }
    }

    // =====================================================================
    // Thanh công cụ mini
    // =====================================================================

    private void buildToolbar() {
        LinearLayout row = (LinearLayout) findViewById(R.id.toolbar_row);
        tbButton(row, R.string.action_open, new Runnable() { public void run() { pickFile(); } });
        tbButton(row, R.string.tb_paste, new Runnable() { public void run() { pasteText(); } });
        tbButton(row, R.string.tb_copy, new Runnable() { public void run() { copyText(); } });
        tbButton(row, R.string.tb_undo, new Runnable() { public void run() { undo(); } });
        tbButton(row, R.string.tb_redo, new Runnable() { public void run() { redo(); } });
        tbButton(row, R.string.tb_search, new Runnable() { public void run() { showSearch(false); } });
        tbButton(row, R.string.action_save, new Runnable() { public void run() { save(); } });
        tbButton(row, R.string.tb_run, new Runnable() { public void run() { runFile(); } });
        tbButton(row, R.string.pp_indent, new Runnable() { public void run() { shiftIndent(true); } });
        tbButton(row, R.string.pp_outdent, new Runnable() { public void run() { shiftIndent(false); } });
        tbButton(row, R.string.action_color, new Runnable() { public void run() { pickColor(); } });
        tbButton(row, R.string.tb_close, new Runnable() { public void run() { closeTab(sCur); } });
    }

    private void tbButton(LinearLayout row, int labelRes, final Runnable action) {
        Button b = new Button(this, null, android.R.attr.borderlessButtonStyle);
        b.setText(labelRes);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        row.addView(b);
    }

    private void tintToolbar(int color) {
        LinearLayout row = (LinearLayout) findViewById(R.id.toolbar_row);
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            if (v instanceof Button) ((Button) v).setTextColor(color);
        }
    }

    // =====================================================================
    // Clipboard
    // =====================================================================

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
        edit(a, b, s);
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
        edit(a, b, "");
    }

    // =====================================================================
    // Tìm kiếm, thay thế, nhảy tới dòng
    // =====================================================================

    private void setupSearchPanel() {
        findInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent ev) {
                findNext(true);
                return true;
            }
        });
        findViewById(R.id.btn_next).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                findNext(true);
            }
        });
        findViewById(R.id.btn_prev).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                findNext(false);
            }
        });
        findViewById(R.id.btn_replace).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                replaceCurrent();
            }
        });
        findViewById(R.id.btn_replace_all).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                replaceAll();
            }
        });
        findViewById(R.id.btn_search_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchPanel.setVisibility(View.GONE);
            }
        });
    }

    private void showSearch(boolean focusReplace) {
        searchPanel.setVisibility(View.VISIBLE);
        int a = Math.min(editor.getSelectionStart(), editor.getSelectionEnd());
        int b = Math.max(editor.getSelectionStart(), editor.getSelectionEnd());
        if (a >= 0 && b > a && b - a < 200 && editor.getText().subSequence(a, b).toString().indexOf('\n') < 0) {
            findInput.setText(editor.getText().subSequence(a, b).toString());
        }
        (focusReplace ? replaceInput : findInput).requestFocus();
    }

    private Pattern buildPattern() {
        String q = findInput.getText().toString();
        if (q.isEmpty()) return null;
        int flags = 0;
        if (!tgCase.isChecked()) flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        if (tgRegex.isChecked()) flags |= Pattern.MULTILINE;
        else flags |= Pattern.LITERAL;
        try {
            return Pattern.compile(q, flags);
        } catch (PatternSyntaxException e) {
            Toast.makeText(this, String.valueOf(e.getDescription()), Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private void selectRange(int s, int e) {
        editor.requestFocus();
        editor.setSelection(s, e);
        scrollIntoView(s);
    }

    private void findNext(boolean forward) {
        Pattern p = buildPattern();
        if (p == null) return;
        String text = editor.getText().toString();
        Matcher m = p.matcher(text);
        int selS = Math.min(editor.getSelectionStart(), editor.getSelectionEnd());
        int selE = Math.max(editor.getSelectionStart(), editor.getSelectionEnd());
        if (selS < 0) {
            selS = 0;
            selE = 0;
        }
        if (forward) {
            if (m.find(Math.min(selE, text.length()))) {
                selectRange(m.start(), m.end());
                return;
            }
            if (m.find(0)) {
                selectRange(m.start(), m.end());
                Toast.makeText(this, R.string.search_wrapped, Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            int beforeS = -1;
            int beforeE = -1;
            int lastS = -1;
            int lastE = -1;
            int pos = 0;
            while (pos <= text.length() && m.find(pos)) {
                lastS = m.start();
                lastE = m.end();
                if (m.start() < selS) {
                    beforeS = m.start();
                    beforeE = m.end();
                }
                pos = Math.max(m.end(), m.start() + 1);
            }
            if (beforeS >= 0) {
                selectRange(beforeS, beforeE);
                return;
            }
            if (lastS >= 0) {
                selectRange(lastS, lastE);
                Toast.makeText(this, R.string.search_wrapped, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        Toast.makeText(this, R.string.search_not_found, Toast.LENGTH_SHORT).show();
    }

    private String replacementString() {
        String r = replaceInput.getText().toString();
        return tgRegex.isChecked() ? r : Matcher.quoteReplacement(r);
    }

    private void replaceCurrent() {
        Pattern p = buildPattern();
        if (p == null) return;
        int a = Math.min(editor.getSelectionStart(), editor.getSelectionEnd());
        int b = Math.max(editor.getSelectionStart(), editor.getSelectionEnd());
        if (a >= 0 && b > a) {
            String sel = editor.getText().subSequence(a, b).toString();
            if (p.matcher(sel).matches()) {
                try {
                    String rep = p.matcher(sel).replaceFirst(replacementString());
                    edit(a, b, rep);
                    editor.setSelection(a + rep.length());
                } catch (RuntimeException e) {
                    Toast.makeText(this, String.valueOf(e.getMessage()), Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }
        findNext(true);
    }

    private void replaceAll() {
        Pattern p = buildPattern();
        if (p == null) return;
        String text = editor.getText().toString();
        String out;
        int count = 0;
        try {
            Matcher m = p.matcher(text);
            while (m.find()) count++;
            out = p.matcher(text).replaceAll(replacementString());
        } catch (RuntimeException e) {
            Toast.makeText(this, String.valueOf(e.getMessage()), Toast.LENGTH_SHORT).show();
            return;
        }
        if (count > 0 && !out.equals(text)) {
            // Chỉ thay đoạn khác biệt để lịch sử hoàn tác gọn và con trỏ ít bị xê dịch.
            int len1 = text.length();
            int len2 = out.length();
            int max = Math.min(len1, len2);
            int pre = 0;
            while (pre < max && text.charAt(pre) == out.charAt(pre)) pre++;
            int suf = 0;
            while (suf < max - pre && text.charAt(len1 - 1 - suf) == out.charAt(len2 - 1 - suf)) suf++;
            edit(pre, len1 - suf, out.substring(pre, len2 - suf));
        }
        Toast.makeText(this, getString(R.string.replace_done, count), Toast.LENGTH_SHORT).show();
    }

    private void gotoLineDialog() {
        final EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_TEXT);
        in.setHint(getString(R.string.goto_hint, editor.totalLines()));
        in.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_goto)
                .setView(in)
                .setPositiveButton(android.R.string.ok, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        String[] parts = in.getText().toString().trim().split("[:,\\s]+");
                        try {
                            int line = Integer.parseInt(parts[0]);
                            int col = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                            gotoLine(line, col);
                        } catch (NumberFormatException e) {
                            Toast.makeText(MainActivity.this, R.string.goto_invalid, Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void gotoLine(int line, int col) {
        int total = editor.totalLines();
        int idx = Math.max(1, Math.min(line, total)) - 1;
        int start = editor.offsetOfLine(idx);
        int end = idx + 1 < total ? editor.offsetOfLine(idx + 1) - 1 : editor.length();
        int off = Math.min(end, start + Math.max(0, col - 1));
        editor.requestFocus();
        editor.setSelection(off);
        scrollIntoView(off);
    }

    /** Cuộn để vị trí off nằm ở khoảng một phần ba màn hình tính từ trên xuống. */
    private void scrollIntoView(final int off) {
        editor.post(new Runnable() {
            @Override
            public void run() {
                Layout l = editor.getLayout();
                if (l == null) return;
                int o = Math.max(0, Math.min(off, editor.length()));
                int lineIdx = l.getLineForOffset(o);
                int y = l.getLineTop(lineIdx) - editor.getHeight() / 3;
                int max = Math.max(0, l.getHeight() + editor.getTotalPaddingTop()
                        + editor.getTotalPaddingBottom() - editor.getHeight());
                editor.scrollTo(editor.getScrollX(), Math.max(0, Math.min(y, max)));
            }
        });
    }

    // =====================================================================
    // Mở và lưu file
    // =====================================================================

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_OPEN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQ_OPEN) {
            ArrayList<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) {
                ClipData cd = data.getClipData();
                for (int i = 0; i < cd.getItemCount(); i++) uris.add(cd.getItemAt(i).getUri());
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            for (Uri u : uris) {
                persist(u);
                openBackend(new Backends.Saf(this, u));
            }
        } else if (requestCode == REQ_SAVE_AS && data.getData() != null) {
            Uri u = data.getData();
            persist(u);
            doSave(cur(), new Backends.Saf(this, u), null);
        }
    }

    private void persist(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception ignored) {
            // Một số provider không hỗ trợ quyền lâu dài.
        }
    }

    private static final class Loaded {
        String text;
        Encodings.Result enc;
    }

    /** Đọc file ở luồng nền, nhận diện bảng mã, rồi mở trong một thẻ mới. */
    private void openBackend(final Backend b) {
        for (int i = 0; i < sDocs.size(); i++) {
            Backend ob = sDocs.get(i).backend;
            if (ob instanceof Backends.Saf && b instanceof Backends.Saf
                    && ((Backends.Saf) ob).uri.equals(((Backends.Saf) b).uri)) {
                switchTo(i);
                return;
            }
        }
        Toast.makeText(this, R.string.loading, Toast.LENGTH_SHORT).show();
        Net.run(new Net.Task<Loaded>() {
            @Override
            public Loaded call() throws Exception {
                byte[] data = b.read();
                if (data.length > MAX_FILE) throw new IOException(getString(R.string.too_large));
                Loaded l = new Loaded();
                l.enc = Encodings.detect(data);
                l.text = Encodings.decode(data, l.enc.charset, l.enc.bomLength);
                return l;
            }
        }, new Net.Done<Loaded>() {
            @Override
            public void done(Loaded l, Exception err) {
                if (err != null) {
                    Toast.makeText(MainActivity.this,
                            getString(R.string.open_failed) + ": " + err.getMessage(), Toast.LENGTH_LONG).show();
                    return;
                }
                Doc d = new Doc();
                d.backend = b;
                d.text = l.text;
                d.charset = l.enc.charset;
                d.bom = l.enc.bomLength > 0;
                addDoc(d);
            }
        });
    }

    private void save() {
        Doc d = cur();
        if (d.backend == null) {
            saveAs();
        } else {
            doSave(d, d.backend, null);
        }
    }

    private void doSave(final Doc d, final Backend b, String message) {
        if (b.wantsMessage() && message == null) {
            final EditText in = new EditText(this);
            in.setText(getString(R.string.commit_default, d.title(getString(R.string.untitled))));
            new AlertDialog.Builder(this)
                    .setTitle(R.string.commit_message)
                    .setView(in)
                    .setPositiveButton(R.string.action_save, new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface di, int w) {
                            doSave(d, b, in.getText().toString());
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }
        final String text = textOf(d);
        final byte[] data = Encodings.encode(text, d.charset, d.bom);
        final Object token = d.undo.token();
        final String msg = message;
        Net.run(new Net.Task<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                b.write(data, msg);
                return Boolean.TRUE;
            }
        }, new Net.Done<Boolean>() {
            @Override
            public void done(Boolean ok, Exception err) {
                if (err != null) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle(R.string.save_failed)
                            .setMessage(String.valueOf(err.getMessage()))
                            .setPositiveButton(R.string.close, null)
                            .show();
                    return;
                }
                d.backend = b;
                d.undo.markSavedAt(token);
                d.encodingChanged = false;
                Toast.makeText(MainActivity.this, R.string.saved, Toast.LENGTH_SHORT).show();
                if (d == cur()) {
                    lastDirty = d.dirty();
                    configureHighlight(true);
                    updateTitle();
                    updateStatus();
                }
                renderTabs();
            }
        });
    }

    private void saveAs() {
        String[] items = {getString(R.string.save_where_device), getString(R.string.save_where_remote),
                getString(R.string.save_where_root)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_save_as)
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        if (which == 0) saveAsDevice();
                        else if (which == 1) saveAsRemote();
                        else saveAsRoot();
                    }
                })
                .show();
    }

    private String suggestedName() {
        Doc d = cur();
        String n = d.fileName();
        return n.isEmpty() ? "untitled.txt" : n;
    }

    private void saveAsDevice() {
        String name = suggestedName();
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(name.endsWith(".txt") ? "text/plain" : "application/octet-stream");
        i.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(i, REQ_SAVE_AS);
    }

    private void saveAsRemote() {
        RemoteUi.connect(this, true, new RemoteUi.ConnectListener() {
            @Override
            public void onFile(RemoteFs fs, String path) {
                // không dùng: chỉ chọn thư mục
            }

            @Override
            public void onFolder(final RemoteFs fs, final String dir) {
                final EditText in = new EditText(MainActivity.this);
                in.setText(suggestedName());
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.file_name)
                        .setView(in)
                        .setPositiveButton(R.string.action_save, new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface di, int w) {
                                String n = in.getText().toString().trim();
                                if (n.isEmpty()) return;
                                doSave(cur(), new Backends.Remote(fs, RemoteFsImpl.join(dir, n)), null);
                            }
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            }
        });
    }

    private void saveAsRoot() {
        final EditText in = new EditText(this);
        in.setText("/" + suggestedName());
        new AlertDialog.Builder(this)
                .setTitle(R.string.root_path_title)
                .setView(in)
                .setPositiveButton(R.string.action_save, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface di, int w) {
                        final String path = in.getText().toString().trim();
                        if (path.isEmpty()) return;
                        withRoot(new Runnable() {
                            @Override
                            public void run() {
                                doSave(cur(), new Backends.Root(path), null);
                            }
                        });
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // =====================================================================
    // Máy chủ từ xa, Git và root
    // =====================================================================

    private void openRemote() {
        RemoteUi.connect(this, false, new RemoteUi.ConnectListener() {
            @Override
            public void onFile(RemoteFs fs, String path) {
                openBackend(new Backends.Remote(fs, path));
            }

            @Override
            public void onFolder(RemoteFs fs, String dir) {
                // không dùng khi mở file
            }
        });
    }

    private void withRoot(final Runnable then) {
        Net.run(new Net.Task<Boolean>() {
            @Override
            public Boolean call() {
                return Backends.Root.available();
            }
        }, new Net.Done<Boolean>() {
            @Override
            public void done(Boolean ok, Exception err) {
                if (ok != null && ok) then.run();
                else Toast.makeText(MainActivity.this, R.string.root_unavailable, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openRoot() {
        withRoot(new Runnable() {
            @Override
            public void run() {
                RemoteUi.browse(MainActivity.this, "/", null, false, new RemoteUi.Lister() {
                    @Override
                    public List<RemoteEntry> list(String dir) throws Exception {
                        return Backends.Root.list(dir);
                    }
                }, new RemoteUi.BrowseListener() {
                    @Override
                    public void onFile(String path) {
                        openBackend(new Backends.Root(path));
                    }

                    @Override
                    public void onFolder(String dir) {
                        // không dùng khi mở file
                    }
                });
            }
        });
    }

    // =====================================================================
    // Bảng mã
    // =====================================================================

    private void showEncodingDialog() {
        final Doc d = cur();
        String[] items = {
                getString(R.string.enc_reopen),
                getString(R.string.enc_save_as),
                getString(R.string.enc_bom, d.bom ? "ON" : "OFF")};
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.action_encoding) + ": " + d.encodingLabel())
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface di, int which) {
                        if (which == 0) pickEncoding(true);
                        else if (which == 1) pickEncoding(false);
                        else {
                            d.bom = !d.bom;
                            d.encodingChanged = true;
                            afterEncodingChange();
                        }
                    }
                })
                .show();
    }

    private void afterEncodingChange() {
        lastDirty = cur().dirty();
        updateTitle();
        updateStatus();
        renderTabs();
    }

    private void pickEncoding(final boolean reopen) {
        final List<String> names = Encodings.choices();
        new AlertDialog.Builder(this)
                .setTitle(reopen ? R.string.enc_reopen : R.string.enc_save_as)
                .setItems(names.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface di, int which) {
                        Charset cs = Encodings.forName(names.get(which));
                        if (reopen) reopenWith(cs);
                        else {
                            Doc d = cur();
                            d.charset = cs;
                            d.bom = false;
                            d.encodingChanged = true;
                            afterEncodingChange();
                        }
                    }
                })
                .show();
    }

    /** Đọc lại file từ nguồn và giải mã bằng bảng mã đã chọn. */
    private void reopenWith(final Charset cs) {
        final Doc d = cur();
        if (d.backend == null) {
            Toast.makeText(this, R.string.enc_need_file, Toast.LENGTH_SHORT).show();
            return;
        }
        confirmDiscard(d, new Runnable() {
            @Override
            public void run() {
                Net.run(new Net.Task<Loaded>() {
                    @Override
                    public Loaded call() throws Exception {
                        byte[] data = d.backend.read();
                        Encodings.Result det = Encodings.detect(data);
                        int skip = det.bomLength > 0 && det.charset.equals(cs) ? det.bomLength : 0;
                        Loaded l = new Loaded();
                        l.enc = new Encodings.Result(cs, skip);
                        l.text = Encodings.decode(data, cs, skip);
                        return l;
                    }
                }, new Net.Done<Loaded>() {
                    @Override
                    public void done(Loaded l, Exception err) {
                        if (err != null) {
                            Toast.makeText(MainActivity.this, String.valueOf(err.getMessage()), Toast.LENGTH_LONG).show();
                            return;
                        }
                        d.charset = cs;
                        d.bom = l.enc.bomLength > 0;
                        d.encodingChanged = false;
                        d.undo.clear();
                        d.text = l.text;
                        d.selStart = 0;
                        d.selEnd = 0;
                        d.scrollY = 0;
                        if (d == cur()) showDoc(d);
                    }
                });
            }
        });
    }

    private void chooseLanguage() {
        final List<String> names = Langs.names();
        String[] items = new String[names.size() + 1];
        items[0] = getString(R.string.lang_auto);
        for (int i = 0; i < names.size(); i++) items[i + 1] = names.get(i);
        new AlertDialog.Builder(this)
                .setTitle(R.string.lang_pick)
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface di, int which) {
                        cur().langOverride = which == 0 ? null : names.get(which - 1);
                        configureHighlight(true);
                        updateStatus();
                    }
                })
                .show();
    }

    // =====================================================================
    // Chọn màu
    // =====================================================================

    private static int parseHex(String hex) {
        String h = hex;
        if (h.length() == 4) {
            h = "#" + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2) + h.charAt(3) + h.charAt(3);
        }
        return Color.parseColor(h);
    }

    private void pickColor() {
        String text = editor.getText().toString();
        int a = Math.max(0, Math.min(editor.getSelectionStart(), editor.getSelectionEnd()));
        int b = Math.max(0, Math.max(editor.getSelectionStart(), editor.getSelectionEnd()));
        int ra = a;
        int rb = b;
        int initial = 0xFF2196F3;

        int ls = text.lastIndexOf('\n', Math.max(0, a - 1)) + 1;
        int le = text.indexOf('\n', b);
        if (le < 0) le = text.length();
        Matcher m = HEX_COLOR.matcher(text.substring(ls, le));
        while (m.find()) {
            int s = ls + m.start();
            int e = ls + m.end();
            if (a >= s && b <= e) {
                ra = s;
                rb = e;
                try {
                    initial = parseHex(m.group());
                } catch (IllegalArgumentException ignored) {
                    // giữ màu mặc định
                }
                break;
            }
        }
        final int fa = ra;
        final int fb = rb;
        ColorPickerDialog.show(this, initial, new ColorPickerDialog.Callback() {
            @Override
            public void onColor(int rgb) {
                String hex = String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
                edit(fa, fb, hex);
                editor.setSelection(fa + hex.length());
            }
        });
    }

    // =====================================================================
    // Xem trước và chạy
    // =====================================================================

    private static String ext(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    private boolean canPreview() {
        Lang l = cur().lang();
        if (l == null) return false;
        String e = ext(cur().fileName());
        return (l.kind == Lang.HTML && (!l.xml || e.equals("svg")))
                || l.kind == Lang.CSS
                || l.kind == Lang.MARKDOWN;
    }

    private void preview() {
        if (!canPreview()) {
            Toast.makeText(this, R.string.not_previewable, Toast.LENGTH_SHORT).show();
            return;
        }
        Lang l = cur().lang();
        String text = editor.getText().toString();
        if (l.kind == Lang.HTML) PreviewDialog.showHtml(this, text, dark);
        else if (l.kind == Lang.CSS) PreviewDialog.showAsset(this, "css", text, dark);
        else PreviewDialog.showAsset(this, l.adoc ? "adoc" : "md", text, dark);
    }

    /** "Thực hiện": xem trước nếu là HTML/CSS/Markdown/AsciiDoc, chạy cục bộ nếu là JS, còn lại chạy online. */
    private void runFile() {
        if (canPreview()) {
            preview();
            return;
        }
        String e = ext(cur().fileName());
        if (e.equals("js") || e.equals("mjs")) {
            PreviewDialog.runJs(this, editor.getText().toString());
            return;
        }
        runOnline();
    }

    private void runOnline() {
        Lang l = cur().lang();
        final String name = l == null ? null : l.name;
        if (OnlineCompiler.supports(name)) {
            confirmCompiler(name);
            return;
        }
        final List<String> names = OnlineCompiler.supportedNames();
        new AlertDialog.Builder(this)
                .setTitle(R.string.run_as)
                .setItems(names.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface di, int which) {
                        confirmCompiler(names.get(which));
                    }
                })
                .show();
    }

    private void confirmCompiler(final String langName) {
        final SharedPreferences p = Prefs.get(this);
        final String base = p.getString(Prefs.COMPILER_URL, Prefs.DEFAULT_COMPILER);
        if (base.equals(p.getString(Prefs.COMPILER_OK, ""))) {
            askStdin(langName, base);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_run_online)
                .setMessage(getString(R.string.compiler_consent, base))
                .setPositiveButton(android.R.string.ok, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface di, int w) {
                        p.edit().putString(Prefs.COMPILER_OK, base).apply();
                        askStdin(langName, base);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void askStdin(final String langName, final String base) {
        final EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        in.setMinLines(3);
        in.setGravity(Gravity.TOP | Gravity.START);
        in.setText(lastStdin);
        in.setHint(R.string.run_stdin);
        new AlertDialog.Builder(this)
                .setTitle(langName)
                .setView(in)
                .setPositiveButton(R.string.tb_run, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface di, int w) {
                        lastStdin = in.getText().toString();
                        runOnServer(langName, base, lastStdin);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void runOnServer(final String langName, final String base, final String stdin) {
        final String code = editor.getText().toString();
        final String token = Prefs.get(this).getString(Prefs.COMPILER_TOKEN, "");
        Toast.makeText(this, R.string.running, Toast.LENGTH_SHORT).show();
        Net.run(new Net.Task<String>() {
            @Override
            public String call() throws Exception {
                return OnlineCompiler.execute(base, token, langName, code, stdin);
            }
        }, new Net.Done<String>() {
            @Override
            public void done(String report, Exception err) {
                String body = err != null ? String.valueOf(err.getMessage()) : report;
                TextView tv = new TextView(MainActivity.this);
                tv.setText(body);
                tv.setTypeface(Typeface.MONOSPACE);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                tv.setTextIsSelectable(true);
                tv.setPadding(dp(16), dp(12), dp(16), dp(12));
                ScrollView sv = new ScrollView(MainActivity.this);
                sv.addView(tv);
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(err != null ? getString(R.string.error) : getString(R.string.run_output))
                        .setView(sv)
                        .setPositiveButton(R.string.close, null)
                        .show();
            }
        });
    }

    // =====================================================================
    // Menu mini khi nhấn giữ
    // =====================================================================

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

        if (canPaste) addPopupItem(box, pw, R.string.tb_paste, new Runnable() { public void run() { pasteText(); } });
        addPopupItem(box, pw, R.string.pp_select_all, new Runnable() { public void run() { editor.selectAll(); } });
        if (hasSelection) {
            addPopupItem(box, pw, R.string.tb_copy, new Runnable() { public void run() { copyText(); } });
            addPopupItem(box, pw, R.string.pp_cut, new Runnable() { public void run() { cutText(); } });
        }
        if (nearText) {
            addPopupItem(box, pw, R.string.pp_indent, new Runnable() { public void run() { shiftIndent(true); } });
            addPopupItem(box, pw, R.string.pp_outdent, new Runnable() { public void run() { shiftIndent(false); } });
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
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pw[0] != null) pw[0].dismiss();
                action.run();
            }
        });
        box.addView(tv);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
