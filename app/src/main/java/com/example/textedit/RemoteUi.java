package com.example.textedit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Giao diện kết nối máy chủ (FTP/FTPS/SFTP/GitHub/GitLab) và duyệt thư mục. */
final class RemoteUi {

    interface ConnectListener {
        void onFile(RemoteFs fs, String path);

        void onFolder(RemoteFs fs, String dir);
    }

    interface BrowseListener {
        void onFile(String path);

        void onFolder(String dir);
    }

    interface Lister {
        List<RemoteEntry> list(String dir) throws Exception;
    }

    private static final String[] TYPES = {"FTP", "FTPS (FTP + TLS)", "SFTP", "GitHub", "GitLab"};
    private static final String[] KEYS = {"ftp", "ftps", "sftp", "github", "gitlab"};

    private static final class Field {
        final String key;
        final int label;
        final boolean secret;
        final String def;

        Field(String key, int label, boolean secret, String def) {
            this.key = key;
            this.label = label;
            this.secret = secret;
            this.def = def;
        }
    }

    private RemoteUi() { }

    private static Field[] fields(int type) {
        switch (type) {
            case 0:
            case 1:
                return new Field[]{
                        new Field("host", R.string.f_host, false, ""),
                        new Field("port", R.string.f_port, false, "21"),
                        new Field("user", R.string.f_user, false, ""),
                        new Field("pass", R.string.f_password, true, ""),
                        new Field("dir", R.string.f_dir, false, "/")};
            case 2:
                return new Field[]{
                        new Field("host", R.string.f_host, false, ""),
                        new Field("port", R.string.f_port, false, "22"),
                        new Field("user", R.string.f_user, false, ""),
                        new Field("pass", R.string.f_password, true, ""),
                        new Field("dir", R.string.f_dir, false, "/")};
            case 3:
                return new Field[]{
                        new Field("token", R.string.f_token, true, ""),
                        new Field("repo", R.string.f_repo, false, ""),
                        new Field("branch", R.string.f_branch, false, "main"),
                        new Field("dir", R.string.f_dir_git, false, "")};
            default:
                return new Field[]{
                        new Field("url", R.string.f_gitlab_url, false, "https://gitlab.com"),
                        new Field("token", R.string.f_token, true, ""),
                        new Field("project", R.string.f_project, false, ""),
                        new Field("branch", R.string.f_branch, false, "main"),
                        new Field("dir", R.string.f_dir_git, false, "")};
        }
    }

    /** Bước 1: chọn loại kết nối. */
    static void connect(final Activity act, final boolean pickFolder, final ConnectListener l) {
        new AlertDialog.Builder(act)
                .setTitle(R.string.remote_title)
                .setItems(TYPES, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        form(act, which, pickFolder, l);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Bước 2: nhập thông tin kết nối. */
    private static void form(final Activity act, final int type, final boolean pickFolder, final ConnectListener l) {
        final SharedPreferences p = Prefs.get(act);
        final Field[] fs = fields(type);
        final boolean remember = p.getBoolean("rc_remember", false);

        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * act.getResources().getDisplayMetrics().density);
        col.setPadding(pad, pad / 2, pad, 0);

        final EditText[] inputs = new EditText[fs.length];
        for (int i = 0; i < fs.length; i++) {
            TextView label = new TextView(act);
            label.setText(fs[i].label);
            label.setTextSize(12);
            col.addView(label);
            EditText in = new EditText(act);
            in.setSingleLine(true);
            in.setInputType(fs[i].secret
                    ? (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
                    : InputType.TYPE_CLASS_TEXT);
            String saved = fs[i].secret
                    ? (remember ? p.getString("rs_" + KEYS[type] + "_" + fs[i].key, "") : "")
                    : p.getString("rc_" + KEYS[type] + "_" + fs[i].key, fs[i].def);
            in.setText(saved);
            inputs[i] = in;
            col.addView(in);
        }
        final CheckBox cb = new CheckBox(act);
        cb.setText(R.string.f_remember);
        cb.setChecked(remember);
        col.addView(cb);

        ScrollView sv = new ScrollView(act);
        sv.addView(col);

        new AlertDialog.Builder(act)
                .setTitle(TYPES[type])
                .setView(sv)
                .setPositiveButton(R.string.connect, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        java.util.HashMap<String, String> v = new java.util.HashMap<>();
                        SharedPreferences.Editor ed = p.edit();
                        ed.putBoolean("rc_remember", cb.isChecked());
                        for (int i = 0; i < fs.length; i++) {
                            String val = inputs[i].getText().toString().trim();
                            v.put(fs[i].key, val);
                            if (fs[i].secret) {
                                if (cb.isChecked()) ed.putString("rs_" + KEYS[type] + "_" + fs[i].key, val);
                                else ed.remove("rs_" + KEYS[type] + "_" + fs[i].key);
                            } else {
                                ed.putString("rc_" + KEYS[type] + "_" + fs[i].key, val);
                            }
                        }
                        ed.apply();
                        try {
                            RemoteFs fsys = create(act, type, v);
                            startBrowse(act, fsys, pickFolder, l);
                        } catch (Exception e) {
                            Toast.makeText(act, String.valueOf(e.getMessage()), Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static RemoteFs create(Activity act, int type, java.util.Map<String, String> v) throws Exception {
        switch (type) {
            case 0:
            case 1: {
                String host = v.get("host");
                if (host.isEmpty()) throw new Exception("host?");
                return new RemoteFsImpl.Ftp(host, port(v.get("port"), 21), v.get("user"), v.get("pass"),
                        type == 1, v.get("dir"));
            }
            case 2: {
                String host = v.get("host");
                if (host.isEmpty()) throw new Exception("host?");
                SharedPreferences hk = act.getSharedPreferences("hostkeys", Activity.MODE_PRIVATE);
                return new RemoteFsImpl.Sftp(host, port(v.get("port"), 22), v.get("user"), v.get("pass"),
                        v.get("dir"), hk);
            }
            case 3:
                if (v.get("token").isEmpty() || v.get("repo").indexOf('/') < 0) throw new Exception("token / owner/repo?");
                return new RemoteFsImpl.GitHub(v.get("token"), v.get("repo"), v.get("branch"), v.get("dir"));
            default:
                if (v.get("token").isEmpty() || v.get("project").isEmpty()) throw new Exception("token / project?");
                return new RemoteFsImpl.GitLab(v.get("url"), v.get("token"), v.get("project"), v.get("branch"), v.get("dir"));
        }
    }

    private static int port(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Bước 3: kiểm tra kết nối bằng cách liệt kê thư mục đầu, rồi mở trình duyệt thư mục. */
    private static void startBrowse(final Activity act, final RemoteFs fs, final boolean pickFolder, final ConnectListener l) {
        Toast.makeText(act, R.string.connecting, Toast.LENGTH_SHORT).show();
        final String start = fs.start();
        Net.run(new Net.Task<List<RemoteEntry>>() {
            @Override
            public List<RemoteEntry> call() throws Exception {
                return fs.list(start);
            }
        }, new Net.Done<List<RemoteEntry>>() {
            @Override
            public void done(List<RemoteEntry> result, Exception error) {
                if (error != null) {
                    showError(act, error);
                    return;
                }
                browse(act, start, result, pickFolder, new Lister() {
                    @Override
                    public List<RemoteEntry> list(String dir) throws Exception {
                        return fs.list(dir);
                    }
                }, new BrowseListener() {
                    @Override
                    public void onFile(String path) {
                        l.onFile(fs, path);
                    }

                    @Override
                    public void onFolder(String dir) {
                        l.onFolder(fs, dir);
                    }
                });
            }
        });
    }

    static void showError(Activity act, Exception e) {
        String m = e.getMessage();
        new AlertDialog.Builder(act)
                .setTitle(R.string.error)
                .setMessage(m == null ? e.toString() : m)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    // ---------- Trình duyệt thư mục ----------

    static void browse(Activity act, String start, List<RemoteEntry> initial, boolean pickFolder,
                       Lister lister, BrowseListener l) {
        new Browser(act, lister, pickFolder, l).open(start, initial);
    }

    private static String parentOf(String dir) {
        if (dir == null || dir.isEmpty() || dir.equals("/")) return null;
        String d = dir;
        while (d.length() > 1 && d.endsWith("/")) d = d.substring(0, d.length() - 1);
        int i = d.lastIndexOf('/');
        if (i < 0) return "";
        if (i == 0) return "/";
        return d.substring(0, i);
    }

    private static final class Browser {
        final Activity act;
        final Lister lister;
        final boolean pickFolder;
        final BrowseListener l;
        String dir = "";
        AlertDialog dlg;
        ArrayAdapter<String> adapter;
        List<RemoteEntry> entries = new ArrayList<>();
        String parent;

        Browser(Activity act, Lister lister, boolean pickFolder, BrowseListener l) {
            this.act = act;
            this.lister = lister;
            this.pickFolder = pickFolder;
            this.l = l;
        }

        void open(String startDir, List<RemoteEntry> initial) {
            ListView lv = new ListView(act);
            adapter = new ArrayAdapter<>(act, android.R.layout.simple_list_item_1);
            lv.setAdapter(adapter);
            lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> a, View v, int pos, long id) {
                    onItem(pos);
                }
            });
            AlertDialog.Builder b = new AlertDialog.Builder(act).setView(lv)
                    .setNegativeButton(R.string.cancel, null);
            if (pickFolder) {
                b.setPositiveButton(R.string.choose_folder, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        l.onFolder(dir);
                    }
                });
            }
            dlg = b.create();
            dlg.show();
            go(startDir, initial);
        }

        void go(final String target, List<RemoteEntry> initial) {
            dir = target;
            dlg.setTitle(target.isEmpty() ? "/" : target);
            if (initial != null) {
                fill(initial);
                return;
            }
            adapter.clear();
            adapter.add("…");
            Net.run(new Net.Task<List<RemoteEntry>>() {
                @Override
                public List<RemoteEntry> call() throws Exception {
                    return lister.list(target);
                }
            }, new Net.Done<List<RemoteEntry>>() {
                @Override
                public void done(List<RemoteEntry> result, Exception error) {
                    if (error != null) {
                        adapter.clear();
                        Toast.makeText(act, String.valueOf(error.getMessage()), Toast.LENGTH_LONG).show();
                        parent = parentOf(target);
                        if (parent != null) adapter.add("↩ ..");
                        return;
                    }
                    fill(result);
                }
            });
        }

        void fill(List<RemoteEntry> list) {
            entries = list;
            parent = parentOf(dir);
            adapter.clear();
            if (parent != null) adapter.add("↩ ..");
            for (RemoteEntry e : list) adapter.add((e.dir ? "📁 " : "📄 ") + e.name);
        }

        void onItem(int pos) {
            int off = parent != null ? 1 : 0;
            if (parent != null && pos == 0) {
                go(parent, null);
                return;
            }
            int idx = pos - off;
            if (idx < 0 || idx >= entries.size()) return;
            RemoteEntry e = entries.get(idx);
            if (e.dir) {
                go(e.path, null);
            } else if (!pickFolder) {
                dlg.dismiss();
                l.onFile(e.path);
            }
        }
    }
}
