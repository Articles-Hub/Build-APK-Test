package com.example.textedit;

import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Base64;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;

/** Các triển khai RemoteFs: FTP/FTPS, SFTP, GitHub, GitLab. */
final class RemoteFsImpl {
    private RemoteFsImpl() { }

    static String join(String dir, String name) {
        if (dir == null || dir.isEmpty() || dir.equals("/")) return "/" + name;
        return dir.endsWith("/") ? dir + name : dir + "/" + name;
    }

    static void sortEntries(List<RemoteEntry> list) {
        Collections.sort(list, new Comparator<RemoteEntry>() {
            @Override
            public int compare(RemoteEntry a, RemoteEntry b) {
                if (a.dir != b.dir) return a.dir ? -1 : 1;
                return a.name.compareToIgnoreCase(b.name);
            }
        });
    }

    // ---------- FTP / FTPS ----------

    static final class Ftp implements RemoteFs {
        private final String host;
        private final int port;
        private final String user;
        private final String pass;
        private final boolean tls;
        private final String start;

        Ftp(String host, int port, String user, String pass, boolean tls, String start) {
            this.host = host;
            this.port = port;
            this.user = user;
            this.pass = pass;
            this.tls = tls;
            this.start = (start == null || start.isEmpty()) ? "/" : start;
        }

        private FTPClient connect() throws Exception {
            FTPClient c = tls ? new FTPSClient("TLS", false) : new FTPClient();
            c.setControlEncoding("UTF-8");
            c.setConnectTimeout(15000);
            c.setDefaultTimeout(30000);
            c.connect(host, port);
            if (!FTPReply.isPositiveCompletion(c.getReplyCode())) {
                String r = c.getReplyString();
                c.disconnect();
                throw new IOException("FTP: " + r);
            }
            if (!c.login(user.isEmpty() ? "anonymous" : user, pass)) {
                c.disconnect();
                throw new IOException("FTP login failed");
            }
            if (tls) {
                ((FTPSClient) c).execPBSZ(0);
                ((FTPSClient) c).execPROT("P");
            }
            c.enterLocalPassiveMode();
            c.setFileType(FTP.BINARY_FILE_TYPE);
            return c;
        }

        private static void close(FTPClient c) {
            try {
                c.logout();
            } catch (Exception ignored) {
                // bỏ qua
            }
            try {
                c.disconnect();
            } catch (Exception ignored) {
                // bỏ qua
            }
        }

        @Override
        public List<RemoteEntry> list(String dir) throws Exception {
            FTPClient c = connect();
            try {
                FTPFile[] files = c.listFiles(dir);
                ArrayList<RemoteEntry> out = new ArrayList<>();
                if (files != null) {
                    for (FTPFile f : files) {
                        String n = f.getName();
                        if (n == null || n.equals(".") || n.equals("..")) continue;
                        RemoteEntry e = new RemoteEntry();
                        e.name = n;
                        e.dir = f.isDirectory();
                        e.path = join(dir, n);
                        out.add(e);
                    }
                }
                sortEntries(out);
                return out;
            } finally {
                close(c);
            }
        }

        @Override
        public byte[] read(String path) throws Exception {
            FTPClient c = connect();
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                if (!c.retrieveFile(path, out)) throw new IOException("FTP: " + c.getReplyString());
                return out.toByteArray();
            } finally {
                close(c);
            }
        }

        @Override
        public void write(String path, byte[] data, String message) throws Exception {
            FTPClient c = connect();
            try {
                if (!c.storeFile(path, new ByteArrayInputStream(data))) {
                    throw new IOException("FTP: " + c.getReplyString());
                }
            } finally {
                close(c);
            }
        }

        @Override
        public boolean wantsMessage() {
            return false;
        }

        @Override
        public String start() {
            return start;
        }
    }

    // ---------- SFTP ----------

    static final class Sftp implements RemoteFs {
        private final String host;
        private final int port;
        private final String user;
        private final String pass;
        private final String start;
        private final SharedPreferences hostKeys;

        Sftp(String host, int port, String user, String pass, String start, SharedPreferences hostKeys) {
            this.host = host;
            this.port = port;
            this.user = user;
            this.pass = pass;
            this.start = (start == null || start.isEmpty()) ? "/" : start;
            this.hostKeys = hostKeys;
        }

        private Session session() throws Exception {
            JSch jsch = new JSch();
            Session s = jsch.getSession(user, host, port);
            s.setPassword(pass);
            Properties cfg = new Properties();
            cfg.put("StrictHostKeyChecking", "no");
            s.setConfig(cfg);
            s.connect(15000);

            // Tin tưởng ở lần kết nối đầu (TOFU): lưu dấu vân tay, các lần sau phải khớp.
            String fp = s.getHostKey().getFingerPrint(jsch);
            String key = host + ":" + port;
            String saved = hostKeys.getString(key, null);
            if (saved == null) {
                hostKeys.edit().putString(key, fp).apply();
            } else if (!saved.equals(fp)) {
                s.disconnect();
                throw new IOException("Host key changed for " + key + "\nsaved: " + saved + "\nnow:   " + fp);
            }
            return s;
        }

        private ChannelSftp channel(Session s) throws Exception {
            ChannelSftp ch = (ChannelSftp) s.openChannel("sftp");
            ch.connect(15000);
            return ch;
        }

        @Override
        public List<RemoteEntry> list(String dir) throws Exception {
            Session s = session();
            try {
                ChannelSftp ch = channel(s);
                try {
                    ArrayList<RemoteEntry> out = new ArrayList<>();
                    Vector<?> v = ch.ls(dir);
                    for (Object o : v) {
                        ChannelSftp.LsEntry le = (ChannelSftp.LsEntry) o;
                        String n = le.getFilename();
                        if (n.equals(".") || n.equals("..")) continue;
                        RemoteEntry e = new RemoteEntry();
                        e.name = n;
                        e.path = join(dir, n);
                        boolean isDir = le.getAttrs().isDir();
                        if (!isDir && le.getAttrs().isLink()) {
                            try {
                                isDir = ch.stat(e.path).isDir();
                            } catch (Exception ignored) {
                                isDir = false;
                            }
                        }
                        e.dir = isDir;
                        out.add(e);
                    }
                    sortEntries(out);
                    return out;
                } finally {
                    ch.disconnect();
                }
            } finally {
                s.disconnect();
            }
        }

        @Override
        public byte[] read(String path) throws Exception {
            Session s = session();
            try {
                ChannelSftp ch = channel(s);
                try {
                    return Backends.readAll(ch.get(path));
                } finally {
                    ch.disconnect();
                }
            } finally {
                s.disconnect();
            }
        }

        @Override
        public void write(String path, byte[] data, String message) throws Exception {
            Session s = session();
            try {
                ChannelSftp ch = channel(s);
                try {
                    ch.put(new ByteArrayInputStream(data), path, ChannelSftp.OVERWRITE);
                } finally {
                    ch.disconnect();
                }
            } finally {
                s.disconnect();
            }
        }

        @Override
        public boolean wantsMessage() {
            return false;
        }

        @Override
        public String start() {
            return start;
        }
    }

    // ---------- GitHub (REST, cần Personal Access Token) ----------

    static final class GitHub implements RemoteFs {
        private final String token;
        private final String repo;
        private final String branch;
        private final String start;

        GitHub(String token, String repo, String branch, String start) {
            this.token = token;
            this.repo = repo;
            this.branch = (branch == null || branch.isEmpty()) ? "main" : branch;
            this.start = start == null ? "" : start;
        }

        private String url(String path) {
            String p = path.startsWith("/") ? path.substring(1) : path;
            String tail = p.isEmpty() ? "" : "/" + Uri.encode(p, "/");
            return "https://api.github.com/repos/" + repo + "/contents" + tail
                    + "?ref=" + Uri.encode(branch);
        }

        private Map<String, String> headers(String accept) {
            HashMap<String, String> h = new HashMap<>();
            h.put("Authorization", "Bearer " + token);
            h.put("Accept", accept);
            h.put("X-GitHub-Api-Version", "2022-11-28");
            return h;
        }

        private static void check(Net.Response r) throws IOException {
            if (r.code >= 400) {
                String msg = Net.text(r.body);
                try {
                    msg = new JSONObject(msg).optString("message", msg);
                } catch (Exception ignored) {
                    // giữ nguyên thân phản hồi
                }
                throw new IOException("GitHub HTTP " + r.code + ": " + msg);
            }
        }

        @Override
        public List<RemoteEntry> list(String dir) throws Exception {
            Net.Response r = Net.http("GET", url(dir), headers("application/vnd.github+json"), null);
            check(r);
            JSONArray arr = new JSONArray(Net.text(r.body));
            ArrayList<RemoteEntry> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                RemoteEntry e = new RemoteEntry();
                e.name = o.getString("name");
                e.path = o.getString("path");
                e.dir = "dir".equals(o.optString("type"));
                out.add(e);
            }
            sortEntries(out);
            return out;
        }

        @Override
        public byte[] read(String path) throws Exception {
            Net.Response r = Net.http("GET", url(path), headers("application/vnd.github.raw+json"), null);
            check(r);
            return r.body;
        }

        @Override
        public void write(String path, byte[] data, String message) throws Exception {
            String sha = null;
            Net.Response cur = Net.http("GET", url(path), headers("application/vnd.github+json"), null);
            if (cur.code == 200) {
                sha = new JSONObject(Net.text(cur.body)).optString("sha", null);
            } else if (cur.code != 404) {
                check(cur);
            }
            JSONObject body = new JSONObject();
            body.put("message", message == null || message.isEmpty() ? "Update " + path : message);
            body.put("content", Base64.encodeToString(data, Base64.NO_WRAP));
            body.put("branch", branch);
            if (sha != null) body.put("sha", sha);
            Map<String, String> h = headers("application/vnd.github+json");
            h.put("Content-Type", "application/json");
            Net.Response r = Net.http("PUT", url(path), h, body.toString().getBytes("UTF-8"));
            check(r);
        }

        @Override
        public boolean wantsMessage() {
            return true;
        }

        @Override
        public String start() {
            return start;
        }
    }

    // ---------- GitLab (REST, cần Personal Access Token) ----------

    static final class GitLab implements RemoteFs {
        private final String base;
        private final String token;
        private final String project;
        private final String branch;
        private final String start;

        GitLab(String base, String token, String project, String branch, String start) {
            String b = (base == null || base.isEmpty()) ? "https://gitlab.com" : base.trim();
            while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
            this.base = b;
            this.token = token;
            this.project = project;
            this.branch = (branch == null || branch.isEmpty()) ? "main" : branch;
            this.start = start == null ? "" : start;
        }

        private String projectUrl() {
            return base + "/api/v4/projects/" + Uri.encode(project, "");
        }

        private String fileUrl(String path) {
            String p = path.startsWith("/") ? path.substring(1) : path;
            return projectUrl() + "/repository/files/" + Uri.encode(p, "");
        }

        private Map<String, String> headers() {
            HashMap<String, String> h = new HashMap<>();
            h.put("PRIVATE-TOKEN", token);
            return h;
        }

        private static void check(Net.Response r) throws IOException {
            if (r.code >= 400) {
                throw new IOException("GitLab HTTP " + r.code + ": " + Net.text(r.body));
            }
        }

        @Override
        public List<RemoteEntry> list(String dir) throws Exception {
            String d = dir.startsWith("/") ? dir.substring(1) : dir;
            ArrayList<RemoteEntry> out = new ArrayList<>();
            for (int page = 1; page <= 10; page++) {
                String url = projectUrl() + "/repository/tree?path=" + Uri.encode(d)
                        + "&ref=" + Uri.encode(branch) + "&per_page=100&page=" + page;
                Net.Response r = Net.http("GET", url, headers(), null);
                check(r);
                JSONArray arr = new JSONArray(Net.text(r.body));
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    RemoteEntry e = new RemoteEntry();
                    e.name = o.getString("name");
                    e.path = o.getString("path");
                    e.dir = "tree".equals(o.optString("type"));
                    out.add(e);
                }
                if (arr.length() < 100) break;
            }
            sortEntries(out);
            return out;
        }

        @Override
        public byte[] read(String path) throws Exception {
            Net.Response r = Net.http("GET", fileUrl(path) + "/raw?ref=" + Uri.encode(branch), headers(), null);
            check(r);
            return r.body;
        }

        @Override
        public void write(String path, byte[] data, String message) throws Exception {
            Net.Response exists = Net.http("GET", fileUrl(path) + "?ref=" + Uri.encode(branch), headers(), null);
            boolean isNew = exists.code == 404;
            if (!isNew) check(exists);
            JSONObject body = new JSONObject();
            body.put("branch", branch);
            body.put("content", Base64.encodeToString(data, Base64.NO_WRAP));
            body.put("encoding", "base64");
            body.put("commit_message", message == null || message.isEmpty() ? "Update " + path : message);
            Map<String, String> h = headers();
            h.put("Content-Type", "application/json");
            Net.Response r = Net.http(isNew ? "POST" : "PUT", fileUrl(path), h, body.toString().getBytes("UTF-8"));
            check(r);
        }

        @Override
        public boolean wantsMessage() {
            return true;
        }

        @Override
        public String start() {
            return start;
        }
    }
}
