package com.example.textedit;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Các cách đọc/ghi file: Storage Access Framework, quyền root, máy chủ từ xa. */
final class Backends {
    private Backends() { }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try {
            byte[] chunk = new byte[16384];
            int n;
            while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
        } finally {
            in.close();
        }
        return buf.toByteArray();
    }

    // ---------- SAF: file trên thiết bị và các app đám mây (Drive, Dropbox, OneDrive...) ----------

    static final class Saf implements Backend {
        private final Context ctx;
        final Uri uri;
        private String cachedName;

        Saf(Context ctx, Uri uri) {
            this.ctx = ctx.getApplicationContext();
            this.uri = uri;
        }

        @Override
        public byte[] read() throws Exception {
            InputStream in = ctx.getContentResolver().openInputStream(uri);
            if (in == null) throw new IOException("null stream");
            return readAll(in);
        }

        @Override
        public void write(byte[] data, String message) throws Exception {
            // "wt" ghi đè và cắt phần cũ, tránh thừa đuôi khi nội dung ngắn lại.
            OutputStream out = ctx.getContentResolver().openOutputStream(uri, "wt");
            if (out == null) throw new IOException("null stream");
            try {
                out.write(data);
            } finally {
                out.close();
            }
        }

        @Override
        public String name() {
            if (cachedName != null) return cachedName;
            ContentResolver cr = ctx.getContentResolver();
            Cursor c = null;
            try {
                c = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
                if (c != null && c.moveToFirst()) cachedName = c.getString(0);
            } catch (Exception ignored) {
                // rơi về đoạn cuối của đường dẫn
            } finally {
                if (c != null) c.close();
            }
            if (cachedName == null) cachedName = uri.getLastPathSegment();
            return cachedName;
        }

        @Override
        public boolean wantsMessage() {
            return false;
        }
    }

    // ---------- Root: chỉnh sửa file hệ thống trên máy đã root ----------

    static final class Root implements Backend {
        final String path;

        Root(String path) {
            this.path = path;
        }

        static String quote(String s) {
            return "'" + s.replace("'", "'\\''") + "'";
        }

        /** Chạy lệnh qua su; trả về stdout. Ném lỗi kèm stderr nếu lệnh thất bại. */
        static byte[] su(String command, byte[] stdin) throws Exception {
            final Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            final ByteArrayOutputStream err = new ByteArrayOutputStream();
            Thread t = new Thread(() -> {
                try {
                    err.write(readAll(p.getErrorStream()));
                } catch (IOException ignored) {
                    // bỏ qua
                }
            });
            t.start();
            OutputStream os = p.getOutputStream();
            try {
                if (stdin != null) os.write(stdin);
            } finally {
                os.close();
            }
            byte[] out = readAll(p.getInputStream());
            int code = p.waitFor();
            t.join(2000);
            if (code != 0) {
                String msg = new String(err.toByteArray(), "UTF-8").trim();
                throw new IOException(msg.isEmpty() ? "su exit code " + code : msg);
            }
            return out;
        }

        static boolean available() {
            try {
                String id = new String(su("id", null), "UTF-8");
                return id.contains("uid=0");
            } catch (Exception e) {
                return false;
            }
        }

        static List<RemoteEntry> list(String dir) throws Exception {
            String out = new String(su("ls -1ap " + quote(dir), null), "UTF-8");
            ArrayList<RemoteEntry> res = new ArrayList<>();
            for (String line : out.split("\n")) {
                String n = line.trim();
                if (n.isEmpty() || n.equals("./") || n.equals("../")) continue;
                RemoteEntry e = new RemoteEntry();
                e.dir = n.endsWith("/");
                e.name = e.dir ? n.substring(0, n.length() - 1) : n;
                e.path = dir.endsWith("/") ? dir + e.name : dir + "/" + e.name;
                res.add(e);
            }
            return res;
        }

        @Override
        public byte[] read() throws Exception {
            return su("cat " + quote(path), null);
        }

        @Override
        public void write(byte[] data, String message) throws Exception {
            // "cat >" giữ nguyên chủ sở hữu và quyền của file hiện có.
            try {
                su("cat > " + quote(path), data);
            } catch (IOException e) {
                throw new IOException(e.getMessage() + " (read-only file system? try: mount -o remount,rw)");
            }
        }

        @Override
        public String name() {
            int i = path.lastIndexOf('/');
            return i >= 0 ? path.substring(i + 1) : path;
        }

        @Override
        public boolean wantsMessage() {
            return false;
        }
    }

    // ---------- Máy chủ từ xa ----------

    static final class Remote implements Backend {
        final RemoteFs fs;
        final String path;

        Remote(RemoteFs fs, String path) {
            this.fs = fs;
            this.path = path;
        }

        @Override
        public byte[] read() throws Exception {
            return fs.read(path);
        }

        @Override
        public void write(byte[] data, String message) throws Exception {
            fs.write(path, data, message);
        }

        @Override
        public String name() {
            int i = path.lastIndexOf('/');
            return i >= 0 ? path.substring(i + 1) : path;
        }

        @Override
        public boolean wantsMessage() {
            return fs.wantsMessage();
        }
    }
}
