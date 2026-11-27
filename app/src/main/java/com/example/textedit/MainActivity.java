package com.example.textedit;

import android.app.ActionBar;
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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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
    private static final int MENU_INDENT = 9001;
    private static final int MENU_OUTDENT = 9002;
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
    private SearchHighlighter sh;
    private SideDrawer drawer;
    private ToggleButton tgWord;
    private View replaceRow;
    private LinearLayout drawerList;

    private boolean black;
    private boolean autoPair = true;
    private boolean tabSpaces = true;
    private boolean restoreEnabled = true;
    private boolean autoSave;
    private int undoState = -1;
    private int pendingCaret = -1;
    private int pendingDeleteAt = -1;
    private char pendingDeleteChar;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable caretTask = new Runnable() {
        @Override
        public void run() {
            if (pendingCaret >= 0 && pendingCaret <= editor.length()) editor.setSelection(pendingCaret);
            pendingCaret = -1;
        }
    };
    private final Runnable deleteTask = new Runnable() {
        @Override
        public void run() {
            int at = pendingDeleteAt;
            pendingDeleteAt = -1;
            Editable e = editor.getText();
            if (at >= 0 && at < e.length() && e.charAt(at) == pendingDeleteChar) edit(at, at + 1, "");
        }
    };
    private final Runnable sessionTask = new Runnable() {
        @Override
        public void run() {
            saveSession();
        }
    };

    private boolean dark;
    private String cfgAtCreate = "";
    private boolean loading;
    private boolean restoring;
    private boolean programmatic;
    private boolean lastDirty;
    private boolean autoIndent = true;
    private int tabSize = 4;
    private String hlKey = "";
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
        black = Prefs.isBlack(this);
        setContentView(R.layout.activity_main);
        drawer = (SideDrawer) findViewById(R.id.drawer);
        drawerList = (LinearLayout) findViewById(R.id.drawer_list);
        tgWord = (ToggleButton) findViewById(R.id.tg_word);
        replaceRow = findViewById(R.id.replace_row);

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
        sh = new SearchHighlighter(editor);
        editor.setListener(new LineNumberEditText.Listener() {
            @Override
            public void onScroll() {
                hl.onScroll();
                sh.onScrollChanged();
            }

            @Override
            public void onSelection(int selStart, int selEnd) {
                updateStatus();
            }
        });
        editor.setFilters(new InputFilter[]{readOnlyFilter, autoPairFilter, autoIndentFilter});
        installSelectionMenu();
        editor.addTextChangedListener(watcher);

        buildToolbar();
        setupSearchPanel();
        buildDrawer();
        ActionBar ab = getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setHomeAsUpIndicator(R.drawable.ic_menu);
            if (black) ab.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        }
        if (black) getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
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

        restoreEnabled = Prefs.get(this).getBoolean(Prefs.RESTORE, true);
        if (sDocs.isEmpty() && restoreEnabled) {
            int[] curOut = new int[1];
            ArrayList<Doc> restored = Session.load(this, curOut);
            if (restored != null) {
                sDocs.addAll(restored);
                sCur = curOut[0];
            }
        }
        if (sDocs.isEmpty()) {
            sDocs.add(newDoc());
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
        if (autoSave) autoSaveFiles();
        uiHandler.removeCallbacks(sessionTask);
        saveSession();
        if (bgVideo.isPlaying()) bgVideo.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uiHandler.removeCallbacks(sessionTask);
        uiHandler.removeCallbacks(caretTask);
        uiHandler.removeCallbacks(deleteTask);
        if (isFinishing()) {
            sDocs.clear();
            sCur = 0;
            if (!restoreEnabled) Session.clearAsync(getApplicationContext());
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
    public boolean onPrepareOptionsMenu(Menu menu) {
        Doc d = cur();
        MenuItem u = menu.findItem(R.id.action_undo);
        MenuItem r = menu.findItem(R.id.action_redo);
        if (u != null) {
            u.setEnabled(d.undo.canUndo());
            if (u.getIcon() != null) u.getIcon().mutate().setAlpha(d.undo.canUndo() ? 255 : 90);
        }
        if (r != null) {
            r.setEnabled(d.undo.canRedo());
            if (r.getIcon() != null) r.getIcon().mutate().setAlpha(d.undo.canRedo() ? 255 : 90);
        }
        MenuItem ro = menu.findItem(R.id.action_readonly);
        if (ro != null) ro.setChecked(d.readOnly);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            refreshDrawer();
            drawer.toggle();
        } else if (id == R.id.action_undo) {
            undo();
        } else if (id == R.id.action_redo) {
            redo();
        } else if (id == R.id.action_syntax) {
            chooseLanguage();
        } else if (id == R.id.action_stats) {
            showStats();
        } else if (id == R.id.action_timestamp) {
            insertTimestamp();
        } else if (id == R.id.action_readonly) {
            toggleReadOnly();
        } else if (id == R.id.action_new) {
            newTab();
        } else if (id == R.id.action_open) {
            pickFile();
        } else if (id == R.id.action_save) {
            save();
        } else if (id == R.id.action_save_as) {
            saveAs();
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
        if (drawer.isOpen()) {
            drawer.close();
            return;
        }
        if (searchPanel.getVisibility() == View.VISIBLE) {
            hideSearch();
            return;
        }
        if (restoreEnabled) {
            // Các thẻ (kể cả phần chưa lưu) được giữ lại và mở lại ở lần vào app sau.
            finish();
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
                hideSearch();
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
        if (!tabSpaces) {
            edit(a, b, "\t");
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
        editor.setShowSoftInputOnFocus(!d.readOnly);
        undoState = -1;
        invalidateOptionsMenu();
        updateTitle();
        updateStatus();
        renderTabs();
        updateSearchHighlight();
        scheduleSession();
    }

    /** Thẻ trống mới, dùng bảng mã và kiểu xuống dòng mặc định trong Cài đặt. */
    private Doc newDoc() {
        SharedPreferences p = Prefs.get(this);
        Doc d = new Doc();
        d.charset = Encodings.forName(p.getString(Prefs.DEFAULT_ENC, "UTF-8"));
        d.eol = Eol.fromKey(p.getString(Prefs.DEFAULT_EOL, "lf"));
        return d;
    }

    private void newTab() {
        addDoc(newDoc());
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
            sDocs.add(newDoc());
            sCur = 0;
            showDoc(cur());
            return;
        }
        scheduleSession();
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
        int selBg = black ? 0xFF2A2A2A : dark ? 0xFF3C3F41 : 0xFFDDDDDD;
        tabScroll.setBackgroundColor(black ? 0xFF000000 : dark ? 0xFF2B2B2B : 0xFFF2F2F2);
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
            t.setText((d.dirty() ? "● " : "") + (d.readOnly ? "🔒 " : "") + d.title(getString(R.string.untitled)));
            t.setTextColor(fg);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setMaxWidth(dp(150));
            t.setPadding(dp(12), dp(10), dp(4), dp(10));
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (idx == sCur) showTabMenu(v, idx);
                    else switchTo(idx);
                }
            });
            t.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showTabMenu(v, idx);
                    return true;
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

    private void showTabMenu(View anchor, final int idx) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, R.string.tab_close);
        pm.getMenu().add(0, 2, 1, R.string.tab_close_others).setEnabled(sDocs.size() > 1);
        pm.getMenu().add(0, 3, 2, R.string.tab_close_all);
        pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == 1) closeTab(idx);
                else if (item.getItemId() == 2) closeMany(idx);
                else closeMany(-1);
                return true;
            }
        });
        pm.show();
    }

    /** Đóng mọi thẻ trừ thẻ keepIdx (hoặc đóng tất cả nếu keepIdx < 0), có hỏi nếu có thẻ chưa lưu. */
    private void closeMany(int keepIdx) {
        final Doc keep = keepIdx >= 0 && keepIdx < sDocs.size() ? sDocs.get(keepIdx) : null;
        int dirty = 0;
        for (Doc d : sDocs) if (d != keep && d.dirty()) dirty++;
        final Runnable go = new Runnable() {
            @Override
            public void run() {
                stashCurrent();
                sDocs.clear();
                if (keep != null) {
                    sDocs.add(keep);
                } else {
                    sDocs.add(newDoc());
                }
                sCur = 0;
                showDoc(cur());
            }
        };
        if (dirty == 0) {
            go.run();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.unsaved_title)
                .setMessage(getString(R.string.unsaved_exit, dirty))
                .setPositiveButton(R.string.discard, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface di, int w) {
                        go.run();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void refreshDirty() {
        Doc c = cur();
        boolean d = c.dirty();
        if (d != lastDirty) {
            lastDirty = d;
            renderTabs();
            updateTitle();
        }
        int us = (c.undo.canUndo() ? 1 : 0) | (c.undo.canRedo() ? 2 : 0);
        if (us != undoState) {
            undoState = us;
            invalidateOptionsMenu();
        }
    }

    private void updateTitle() {
        Doc d = cur();
        setTitle((d.dirty() ? "* " : "") + (d.readOnly ? "🔒 " : "") + d.title(getString(R.string.untitled)));
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
            scheduleSession();
            if (searchPanel.getVisibility() == View.VISIBLE) sh.schedule();
        }
    };

    private boolean blockedByReadOnly() {
        if (!cur().readOnly) return false;
        Toast.makeText(this, R.string.readonly_blocked, Toast.LENGTH_SHORT).show();
        return true;
    }

    private void undo() {
        if (blockedByReadOnly()) return;
        UndoStack.Op op = cur().undo.undo();
        if (op != null) applyOp(op.start, op.newText.length(), op.oldText);
    }

    private void redo() {
        if (blockedByReadOnly()) return;
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
        scheduleSession();
        if (searchPanel.getVisibility() == View.VISIBLE) sh.schedule();
    }

    /** Sửa văn bản bằng mã (vẫn được ghi vào lịch sử hoàn tác, nhưng không tự thụt lề). */
    private void edit(int a, int b, CharSequence replacement) {
        if (blockedByReadOnly()) return;
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

    private final InputFilter readOnlyFilter = new InputFilter() {
        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            if (loading || sDocs.isEmpty() || !cur().readOnly) return null;
            return dest.subSequence(dstart, dend); // giữ nguyên văn bản
        }
    };

    /** Tự đóng cặp ( [ { " ' ` — quy tắc nằm trong AutoPair. */
    private final InputFilter autoPairFilter = new InputFilter() {
        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            if (!autoPair || loading || restoring || programmatic) return null;
            if (end - start == 1) {
                char c = source.charAt(start);
                int act = AutoPair.onType(dest, dstart, dend, c, cur().lang() != null);
                if (act == AutoPair.PAIR) {
                    pendingCaret = dstart + 1;
                    uiHandler.post(caretTask);
                    return String.valueOf(c) + AutoPair.closer(c);
                }
                if (act == AutoPair.SKIP) {
                    pendingCaret = dend + 1;
                    uiHandler.post(caretTask);
                    return "";
                }
                return null;
            }
            if (end == start && AutoPair.deletesEmptyPair(dest, dstart, dend)) {
                pendingDeleteAt = dstart;
                pendingDeleteChar = AutoPair.closer(dest.charAt(dstart));
                uiHandler.post(deleteTask);
            }
            return null;
        }
    };

    private String indentUnit() {
        return tabSpaces ? spaces(tabSize) : "\t";
    }

    private final InputFilter autoIndentFilter = new InputFilter() {
        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            if (!autoIndent || loading || restoring || programmatic) return null;
            if (end - start != 1 || source.charAt(start) != '\n') return null;
            int ls = dstart;
            while (ls > 0 && dest.charAt(ls - 1) != '\n') ls--;
            int k = ls;
            while (k < dstart && (dest.charAt(k) == ' ' || dest.charAt(k) == '\t')) k++;
            String base = dest.subSequence(ls, k).toString();
            StringBuilder indent = new StringBuilder("\n");
            indent.append(base);
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
                if (extra) {
                    String unit = indentUnit();
                    boolean between = AutoPair.isBracketOpen(last) && p == dstart - 1
                            && dend < dest.length() && dest.charAt(dend) == AutoPair.closer(last);
                    indent.append(unit);
                    if (between) {
                        // Enter giữa { } : đưa dấu đóng xuống dòng riêng, con trỏ ở dòng thụt vào.
                        pendingCaret = dstart + indent.length();
                        uiHandler.post(caretTask);
                        indent.append('\n').append(base);
                    }
                }
            }
            return indent.toString();
        }
    };

    /** Menu nổi khi chọn chữ (tay cầm giọt nước, kính lúp là của Android): thêm Thụt vào / Thụt ra. */
    private void installSelectionMenu() {
        ActionMode.Callback cb = new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                menu.add(0, MENU_INDENT, 100, R.string.pp_indent);
                menu.add(0, MENU_OUTDENT, 101, R.string.pp_outdent);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (item.getItemId() == MENU_INDENT) {
                    shiftIndent(true);
                    return true;
                }
                if (item.getItemId() == MENU_OUTDENT) {
                    shiftIndent(false);
                    return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                // không cần dọn gì
            }
        };
        editor.setCustomSelectionActionModeCallback(cb);
        if (Build.VERSION.SDK_INT >= 23) editor.setCustomInsertionActionModeCallback(cb);
    }

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

        String pad = indentUnit();
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
        editor.setLineSpacing(0f, Math.max(10, Math.min(20, p.getInt(Prefs.LINE_SPACING, 10))) / 10f);
        editor.setShowWhitespace(p.getBoolean(Prefs.SHOW_WS, false));

        int textColor = p.getInt(Prefs.TEXT_COLOR, Prefs.defaultTextColor(this));
        editor.setTextColor(textColor);
        boolean lightText = HighlightController.isLight(textColor);
        int base = lightText ? (black ? Color.BLACK : 0xFF1E1E1E) : Color.WHITE;

        int alpha = Math.max(0, Math.min(100, p.getInt(Prefs.OVERLAY, 0))) * 255 / 100;
        editor.setBackgroundColor(Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base)));
        editor.setGutterColor(lightText ? (black ? 0xFF0A0A0A : 0xFF262626) : 0xFFF2F2F2);

        editor.setHorizontallyScrolling(!p.getBoolean(Prefs.WRAP, true));
        editor.setShowLineNumbers(p.getBoolean(Prefs.LINES, false));
        editor.refreshGutter();

        autoIndent = p.getBoolean(Prefs.AUTOINDENT, true);
        autoPair = p.getBoolean(Prefs.AUTOPAIR, true);
        tabSpaces = p.getBoolean(Prefs.TAB_SPACES, true);
        autoSave = p.getBoolean(Prefs.AUTOSAVE, false);
        restoreEnabled = p.getBoolean(Prefs.RESTORE, true);
        tabSize = Math.max(1, p.getInt(Prefs.TAB_SIZE, 4));

        // Màu các thanh theo giao diện sáng/tối.
        int barBg = black ? 0xFF000000 : dark ? 0xFF2B2B2B : 0xFFF2F2F2;
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
        refreshDrawer();

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
                hideSearch();
            }
        });
        findViewById(R.id.btn_expand).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                replaceRow.setVisibility(replaceRow.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            }
        });
        // Tô sáng mọi kết quả ngay khi gõ hoặc khi đổi tùy chọn.
        findInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) { }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) { }

            @Override
            public void afterTextChanged(Editable e) {
                updateSearchHighlight();
            }
        });
        android.widget.CompoundButton.OnCheckedChangeListener opt = new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                updateSearchHighlight();
            }
        };
        tgCase.setOnCheckedChangeListener(opt);
        tgRegex.setOnCheckedChangeListener(opt);
        tgWord.setOnCheckedChangeListener(opt);
    }

    private void hideSearch() {
        searchPanel.setVisibility(View.GONE);
        sh.clear();
    }

    private void updateSearchHighlight() {
        if (sh == null) return;
        if (searchPanel.getVisibility() != View.VISIBLE) {
            sh.clear();
            return;
        }
        sh.setPattern(buildPattern(true));
    }

    private void showSearch(boolean focusReplace) {
        searchPanel.setVisibility(View.VISIBLE);
        if (focusReplace) replaceRow.setVisibility(View.VISIBLE);
        int a = Math.min(editor.getSelectionStart(), editor.getSelectionEnd());
        int b = Math.max(editor.getSelectionStart(), editor.getSelectionEnd());
        if (a >= 0 && b > a && b - a < 200 && editor.getText().subSequence(a, b).toString().indexOf('\n') < 0) {
            findInput.setText(editor.getText().subSequence(a, b).toString());
        }
        (focusReplace ? replaceInput : findInput).requestFocus();
        updateSearchHighlight();
    }

    private Pattern buildPattern() {
        return buildPattern(false);
    }

    private Pattern buildPattern(boolean quiet) {
        try {
            return Finder.build(findInput.getText().toString(), tgCase.isChecked(), tgRegex.isChecked(),
                    tgWord.isChecked());
        } catch (PatternSyntaxException e) {
            if (!quiet) Toast.makeText(this, String.valueOf(e.getDescription()), Toast.LENGTH_SHORT).show();
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
        pickFileAt(null);
    }

    /** Mở trình chọn file của Android, bắt đầu từ vị trí initial (Android 8+; máy cũ bỏ qua). */
    private void pickFileAt(Uri initial) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        if (initial != null && Build.VERSION.SDK_INT >= 26) {
            i.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initial);
        }
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
        String eol; // kiểu xuống dòng gốc của file (null nếu file không có dòng nào)
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
                String raw = Encodings.decode(data, l.enc.charset, l.enc.bomLength);
                l.eol = Eol.detect(raw);
                l.text = Eol.normalize(raw);
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
                Doc d = newDoc();
                d.backend = b;
                d.text = l.text;
                d.charset = l.enc.charset;
                d.bom = l.enc.bomLength > 0;
                if (l.eol != null) d.eol = l.eol;
                Recent.add(MainActivity.this, b);
                addDoc(d);
                refreshDrawer();
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
        doSave(d, b, message, false);
    }

    private void doSave(final Doc d, final Backend b, String message, final boolean silent) {
        if (b.wantsMessage() && message == null) {
            if (silent) return; // Git cần lời nhắn commit nên không tự lưu
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
        final String text = Eol.apply(textOf(d), d.eol);
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
                    if (silent) return;
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
                d.forceDirty = false;
                Recent.add(MainActivity.this, b);
                if (isDestroyed()) return;
                if (!silent) Toast.makeText(MainActivity.this, R.string.saved, Toast.LENGTH_SHORT).show();
                if (sDocs.contains(d) && d == cur()) {
                    lastDirty = d.dirty();
                    configureHighlight(true);
                    updateTitle();
                    updateStatus();
                }
                renderTabs();
                refreshDrawer();
                scheduleSession();
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

    /** Mở file từ máy chủ: 0 FTP, 1 FTPS, 2 SFTP, 3 GitHub, 4 GitLab. */
    private void openRemoteType(int type) {
        RemoteUi.connect(this, false, type, new RemoteUi.ConnectListener() {
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
        if (!Prefs.get(this).getBoolean(Prefs.ROOT, false)) {
            Toast.makeText(this, R.string.root_disabled, Toast.LENGTH_LONG).show();
            return;
        }
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
                getString(R.string.enc_bom, d.bom ? "ON" : "OFF"),
                getString(R.string.enc_eol, Eol.label(d.eol))};
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.action_encoding) + ": " + d.encodingLabel())
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface di, int which) {
                        if (which == 0) pickEncoding(true);
                        else if (which == 1) pickEncoding(false);
                        else if (which == 3) pickEol();
                        else {
                            d.bom = !d.bom;
                            d.encodingChanged = true;
                            afterEncodingChange();
                        }
                    }
                })
                .show();
    }

    private void pickEol() {
        final String[] values = {Eol.LF, Eol.CRLF, Eol.CR};
        String[] items = {"LF (Unix, Android, macOS)", "CRLF (Windows)", "CR (Mac cũ)"};
        new AlertDialog.Builder(this)
                .setTitle(R.string.eol_pick)
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface di, int which) {
                        Doc d = cur();
                        if (!values[which].equals(d.eol)) {
                            d.eol = values[which];
                            d.encodingChanged = true;
                        }
                        afterEncodingChange();
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
                        String raw = Encodings.decode(data, cs, skip);
                        l.eol = Eol.detect(raw);
                        l.text = Eol.normalize(raw);
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
                        if (l.eol != null) d.eol = l.eol;
                        d.encodingChanged = false;
                        d.forceDirty = false;
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
    // Thống kê, thời gian, chỉ đọc
    // =====================================================================

    private void showStats() {
        final Doc d = cur();
        final String all = editor.getText().toString();
        int a = Math.max(0, Math.min(editor.getSelectionStart(), editor.getSelectionEnd()));
        int b = Math.max(0, Math.max(editor.getSelectionStart(), editor.getSelectionEnd()));
        final String sel = b > a ? all.substring(a, b) : null;
        Net.run(new Net.Task<Object[]>() {
            @Override
            public Object[] call() {
                TextStats all1 = TextStats.of(all);
                int bytes = Encodings.encode(Eol.apply(all, d.eol), d.charset, d.bom).length;
                return new Object[]{all1, bytes, sel == null ? null : TextStats.of(sel)};
            }
        }, new Net.Done<Object[]>() {
            @Override
            public void done(Object[] r, Exception err) {
                if (err != null || r == null || isDestroyed()) return;
                TextStats st = (TextStats) r[0];
                int bytes = (Integer) r[1];
                TextStats ss = (TextStats) r[2];
                StringBuilder sb = new StringBuilder();
                sb.append(d.title(getString(R.string.untitled))).append('\n')
                        .append(d.encodingLabel()).append("\n\n");
                sb.append(getString(R.string.stats_body, st.lines, st.words, st.chars, st.charsNoSpace,
                        android.text.format.Formatter.formatShortFileSize(MainActivity.this, bytes), bytes));
                if (ss != null) {
                    sb.append("\n\n").append(getString(R.string.stats_selection, ss.chars, ss.words, ss.lines));
                }
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.action_stats)
                        .setMessage(sb.toString())
                        .setPositiveButton(R.string.close, null)
                        .show();
            }
        });
    }

    private void insertTimestamp() {
        int a = Math.max(0, Math.min(editor.getSelectionStart(), editor.getSelectionEnd()));
        int b = Math.max(0, Math.max(editor.getSelectionStart(), editor.getSelectionEnd()));
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        edit(a, b, ts);
        editor.setSelection(a + ts.length());
    }

    private void toggleReadOnly() {
        Doc d = cur();
        d.readOnly = !d.readOnly;
        editor.setShowSoftInputOnFocus(!d.readOnly);
        if (d.readOnly) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(editor.getWindowToken(), 0);
        }
        updateTitle();
        renderTabs();
        invalidateOptionsMenu();
        scheduleSession();
        Toast.makeText(this, d.readOnly ? R.string.readonly_on : R.string.readonly_off, Toast.LENGTH_SHORT).show();
    }

    // =====================================================================
    // Lưu / khôi phục phiên làm việc và tự lưu
    // =====================================================================

    /** Lưu phiên sau khi ngừng gõ vài giây (để mất điện hay bị tắt đột ngột vẫn còn bản sửa dở). */
    private void scheduleSession() {
        if (!restoreEnabled) return;
        uiHandler.removeCallbacks(sessionTask);
        uiHandler.postDelayed(sessionTask, 4000);
    }

    private void saveSession() {
        if (sDocs.isEmpty() || editor == null) return;
        Context app = getApplicationContext();
        if (!restoreEnabled) {
            Session.clearAsync(app);
            return;
        }
        stashCurrent();
        Session.saveAsync(app, Session.capture(sDocs, sCur));
    }

    /** Tự lưu các file đã mở từ thiết bị/đám mây/root khi rời app (máy chủ và Git cần xác nhận nên bỏ qua). */
    private void autoSaveFiles() {
        for (Doc d : new ArrayList<>(sDocs)) {
            if (!d.dirty() || d.backend == null || d.readOnly) continue;
            if (d.backend instanceof Backends.Saf || d.backend instanceof Backends.Root) {
                doSave(d, d.backend, null, true);
            }
        }
    }

    // =====================================================================
    // Ngăn kéo bên trái
    // =====================================================================

    private void buildDrawer() {
        refreshDrawer();
    }

    private void refreshDrawer() {
        if (drawerList == null) return;
        int fg = dark ? 0xFFE0E0E0 : 0xFF212121;
        int sub = dark ? 0xFF9E9E9E : 0xFF757575;
        int bg = black ? 0xFF000000 : dark ? 0xFF2B2B2B : 0xFFFFFFFF;
        findViewById(R.id.drawer_panel).setBackgroundColor(bg);
        drawerList.removeAllViews();

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(fg);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(20), dp(28), dp(20), dp(12));
        drawerList.addView(title);

        drawerHeader(R.string.drawer_recent, sub);
        final List<Recent.Entry> recents = Recent.list(this);
        if (recents.isEmpty()) {
            TextView none = new TextView(this);
            none.setText(R.string.drawer_recent_empty);
            none.setTextColor(sub);
            none.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            none.setPadding(dp(20), dp(6), dp(20), dp(10));
            drawerList.addView(none);
        } else {
            int shown = Math.min(8, recents.size());
            for (int i = 0; i < shown; i++) {
                final Recent.Entry e = recents.get(i);
                drawerItem((e.type.equals("root") ? "⚙ " : "") + e.name, fg, new Runnable() {
                    @Override
                    public void run() {
                        openRecent(e);
                    }
                });
            }
            drawerItem(getString(R.string.drawer_clear), sub, new Runnable() {
                @Override
                public void run() {
                    Recent.clear(MainActivity.this);
                    refreshDrawer();
                }
            }, false);
        }

        drawerHeader(R.string.drawer_storage, sub);
        drawerItem(getString(R.string.drawer_internal), fg, new Runnable() {
            @Override
            public void run() {
                pickFileAt(primaryStorageUri());
            }
        });
        drawerItem(getString(R.string.drawer_sd), fg, new Runnable() {
            @Override
            public void run() {
                openSdCard();
            }
        });
        drawerItem(getString(R.string.drawer_root), fg, new Runnable() {
            @Override
            public void run() {
                openRoot();
            }
        });

        drawerHeader(R.string.drawer_cloud, sub);
        drawerItem(getString(R.string.drawer_drive), fg, cloud("com.google.android.apps.docs", R.string.drawer_drive));
        drawerItem(getString(R.string.drawer_dropbox), fg, cloud("com.dropbox.android", R.string.drawer_dropbox));
        drawerItem(getString(R.string.drawer_onedrive), fg, cloud("com.microsoft.skydrive", R.string.drawer_onedrive));

        drawerHeader(R.string.drawer_servers, sub);
        drawerItem(getString(R.string.drawer_ftp), fg, server(0));
        drawerItem(getString(R.string.drawer_sftp), fg, server(2));
        drawerItem(getString(R.string.drawer_github), fg, server(3));
        drawerItem(getString(R.string.drawer_gitlab), fg, server(4));

        drawerHeader(R.string.action_settings, sub);
        drawerItem(getString(R.string.action_settings), fg, new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
    }

    private Runnable server(final int type) {
        return new Runnable() {
            @Override
            public void run() {
                openRemoteType(type);
            }
        };
    }

    private Runnable cloud(final String pkg, final int nameRes) {
        return new Runnable() {
            @Override
            public void run() {
                String name = getString(nameRes);
                try {
                    getPackageManager().getPackageInfo(pkg, 0);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, getString(R.string.cloud_not_installed, name),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(MainActivity.this, getString(R.string.cloud_hint, name), Toast.LENGTH_LONG).show();
                pickFile();
            }
        };
    }

    private void drawerHeader(int textRes, int color) {
        TextView h = new TextView(this);
        h.setText(textRes);
        h.setTextColor(color);
        h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setAllCaps(true);
        h.setPadding(dp(20), dp(18), dp(20), dp(4));
        drawerList.addView(h);
    }

    private void drawerItem(String text, int color, Runnable action) {
        drawerItem(text, color, action, true);
    }

    private void drawerItem(String text, int color, final Runnable action, boolean closeDrawer) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        t.setPadding(dp(20), dp(12), dp(20), dp(12));
        t.setBackgroundResource(android.R.drawable.list_selector_background);
        final boolean close = closeDrawer;
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (close) drawer.close();
                action.run();
            }
        });
        drawerList.addView(t);
    }

    private void openRecent(Recent.Entry e) {
        if (e.type.equals("root") && !Prefs.get(this).getBoolean(Prefs.ROOT, false)) {
            Toast.makeText(this, R.string.root_disabled, Toast.LENGTH_LONG).show();
            return;
        }
        Backend b = Recent.make(this, e.type, e.ref);
        if (b != null) openBackend(b);
    }

    private static final String STORAGE_AUTHORITY = "com.android.externalstorage.documents";

    private Uri primaryStorageUri() {
        return DocumentsContract.buildDocumentUri(STORAGE_AUTHORITY, "primary:");
    }

    private void openSdCard() {
        if (Build.VERSION.SDK_INT >= 24) {
            StorageManager sm = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
            if (sm != null) {
                for (StorageVolume v : sm.getStorageVolumes()) {
                    String uuid = v.getUuid();
                    if (v.isRemovable() && !v.isPrimary() && uuid != null) {
                        pickFileAt(DocumentsContract.buildDocumentUri(STORAGE_AUTHORITY, uuid + ":"));
                        return;
                    }
                }
            }
        }
        Toast.makeText(this, R.string.no_sd, Toast.LENGTH_LONG).show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
