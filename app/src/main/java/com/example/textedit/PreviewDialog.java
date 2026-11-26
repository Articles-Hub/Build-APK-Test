package com.example.textedit;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

/** Xem trước trực tiếp (HTML, CSS, Markdown, AsciiDoc) và chạy JavaScript cục bộ trong WebView. */
final class PreviewDialog {

    private PreviewDialog() { }

    private static WebView newWebView(Activity a, boolean dark) {
        WebView wv = new WebView(a);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(false);      // vẫn đọc được file:///android_asset
        s.setAllowContentAccess(false);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        wv.setBackgroundColor(dark ? 0xFF1E1E1E : Color.WHITE);
        return wv;
    }

    private static void showFull(Activity a, boolean dark, String title, final WebView wv) {
        final Dialog d = new Dialog(a, dark
                ? android.R.style.Theme_Material_NoActionBar_Fullscreen
                : android.R.style.Theme_Material_Light_NoActionBar_Fullscreen);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int pad = (int) (12 * a.getResources().getDisplayMetrics().density);
        bar.setPadding(pad, 0, 0, 0);
        TextView t = new TextView(a);
        t.setText(title);
        t.setTextSize(16);
        bar.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button close = new Button(a, null, android.R.attr.borderlessButtonStyle);
        close.setText(R.string.close);
        close.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                d.dismiss();
            }
        });
        bar.addView(close);
        root.addView(bar);
        root.addView(wv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        d.setContentView(root);
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(android.content.DialogInterface di) {
                wv.destroy();
            }
        });
        d.show();
    }

    /** Hiển thị trực tiếp mã HTML. */
    static void showHtml(Activity a, String html, boolean dark) {
        WebView wv = newWebView(a, dark);
        wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
        showFull(a, dark, a.getString(R.string.preview), wv);
    }

    /** kind: "md", "adoc" hoặc "css" — kết xuất bằng assets/render.js, hoạt động offline. */
    static void showAsset(Activity a, final String kind, final String text, final boolean dark) {
        final WebView wv = newWebView(a, dark);
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript("render(" + JSONObject.quote(kind) + "," + JSONObject.quote(text)
                        + "," + dark + ")", null);
            }
        });
        wv.loadUrl("file:///android_asset/preview.html");
        showFull(a, dark, a.getString(R.string.preview), wv);
    }

    /** Chạy JavaScript trong WebView ẩn và hiện kết quả console.log. */
    static void runJs(final Activity a, String code) {
        final StringBuilder out = new StringBuilder();
        final WebView wv = new WebView(a);
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

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                out.append(message).append('\n');
                result.confirm();
                return true;
            }
        });
        String html = "<html><body><script>\n" + code + "\n</script></body></html>";
        wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
        // Chờ một chút để script chạy xong rồi hiện kết quả.
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                String result = out.length() == 0 ? a.getString(R.string.run_no_output) : out.toString();
                wv.destroy();
                if (a.isFinishing()) return;
                new AlertDialog.Builder(a)
                        .setTitle(R.string.run_output)
                        .setMessage(result)
                        .setPositiveButton(R.string.close, null)
                        .show();
            }
        }, 1500);
    }
}
