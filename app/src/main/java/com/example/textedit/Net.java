package com.example.textedit;

import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Chạy tác vụ nền và trả kết quả về luồng giao diện; kèm một client HTTP nhỏ. */
final class Net {
    interface Task<T> {
        T call() throws Exception;
    }

    interface Done<T> {
        void done(T result, Exception error);
    }

    static final class Response {
        int code;
        byte[] body = new byte[0];
        String header(String n) {
            return headers == null ? null : headers.get(n);
        }
        Map<String, String> headers;
    }

    private static final ExecutorService EX = Executors.newCachedThreadPool();
    private static final Handler UI = new Handler(Looper.getMainLooper());

    private Net() { }

    static <T> void run(final Task<T> task, final Done<T> done) {
        EX.execute(new Runnable() {
            @Override
            public void run() {
                T r = null;
                Exception err = null;
                try {
                    r = task.call();
                } catch (Exception e) {
                    err = e;
                }
                final T fr = r;
                final Exception fe = err;
                UI.post(new Runnable() {
                    @Override
                    public void run() {
                        done.done(fr, fe);
                    }
                });
            }
        });
    }

    /** Yêu cầu HTTP đồng bộ (gọi từ luồng nền). */
    static Response http(String method, String url, Map<String, String> headers, byte[] body) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(45000);
        c.setRequestMethod(method);
        c.setRequestProperty("User-Agent", "TextEdit-Android");
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) c.setRequestProperty(e.getKey(), e.getValue());
        }
        if (body != null) {
            c.setDoOutput(true);
            OutputStream os = c.getOutputStream();
            try {
                os.write(body);
            } finally {
                os.close();
            }
        }
        Response r = new Response();
        r.code = c.getResponseCode();
        r.headers = new java.util.HashMap<>();
        String next = c.getHeaderField("X-Next-Page");
        if (next != null) r.headers.put("X-Next-Page", next);
        InputStream in = r.code >= 400 ? c.getErrorStream() : c.getInputStream();
        if (in != null) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try {
                byte[] chunk = new byte[16384];
                int n;
                while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
            } finally {
                in.close();
            }
            r.body = buf.toByteArray();
        }
        c.disconnect();
        return r;
    }

    static String text(byte[] b) {
        try {
            return new String(b, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}
