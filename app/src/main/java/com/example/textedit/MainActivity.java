package com.example.textedit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Trình soạn thảo văn bản đơn giản.
 * Dùng Storage Access Framework nên không cần xin quyền truy cập bộ nhớ.
 */
public class MainActivity extends Activity {

    private static final int REQ_OPEN = 1;
    private static final int REQ_SAVE_AS = 2;
    private static final String KEY_URI = "uri";
    private static final String KEY_DIRTY = "dirty";

    private EditText editor;
    private Uri currentUri;
    private boolean dirty;
    private boolean loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        editor = (EditText) findViewById(R.id.editor);

        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                if (!loading && !dirty) {
                    dirty = true;
                    updateTitle();
                }
            }
        });

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
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(KEY_URI, currentUri == null ? null : currentUri.toString());
        out.putBoolean(KEY_DIRTY, dirty);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_new) {
            confirmDiscard(new Runnable() { public void run() { newFile(); } });
        } else if (id == R.id.action_open) {
            confirmDiscard(new Runnable() { public void run() { pickFile(); } });
        } else if (id == R.id.action_save) {
            save();
        } else if (id == R.id.action_save_as) {
            saveAs();
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        confirmDiscard(new Runnable() { public void run() { finish(); } });
    }

    // ---------- Thao tác file ----------

    private void newFile() {
        loading = true;
        editor.setText("");
        loading = false;
        currentUri = null;
        dirty = false;
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
                // Một số provider không hỗ trợ quyền lâu dài; vẫn đọc được trong phiên này.
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
            updateTitle();
        } catch (Exception e) {
            Toast.makeText(this, R.string.open_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void writeUri(Uri uri) {
        try {
            // "wt" = ghi đè và cắt bỏ phần cũ, tránh file bị thừa đuôi khi nội dung ngắn lại.
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
                .setPositiveButton(R.string.discard, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        dirty = false;
                        action.run();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void updateTitle() {
        String name = getString(R.string.untitled);
        if (currentUri != null) {
            String display = queryName(currentUri);
            if (display != null) name = display;
        }
        setTitle((dirty ? "* " : "") + name);
    }

    private String queryName(Uri uri) {
        ContentResolver cr = getContentResolver();
        Cursor c = null;
        try {
            c = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return uri.getLastPathSegment();
    }
}
