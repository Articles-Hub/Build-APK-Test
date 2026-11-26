package com.example.textedit;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.regex.Pattern;

/** Bảng chọn màu HSV kèm ô nhập mã Hex; trả về mã dạng #RRGGBB. */
final class ColorPickerDialog {

    interface Callback {
        void onColor(int rgb);
    }

    private static final Pattern HEX = Pattern.compile("^#?([0-9a-fA-F]{6})$");

    private ColorPickerDialog() { }

    static void show(Activity a, int initial, final Callback cb) {
        final float[] hsv = new float[3];
        Color.colorToHSV(initial | 0xFF000000, hsv);
        final boolean[] busy = {false};

        final int pad = (int) (16 * a.getResources().getDisplayMetrics().density);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, pad, pad, 0);

        final View preview = new View(a);
        col.addView(preview, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (64 * a.getResources().getDisplayMetrics().density)));

        final SeekBar sh = bar(a, col, "H", 360, (int) hsv[0]);
        final SeekBar ss = bar(a, col, "S", 100, (int) (hsv[1] * 100));
        final SeekBar sv = bar(a, col, "V", 100, (int) (hsv[2] * 100));

        final EditText hex = new EditText(a);
        hex.setSingleLine(true);
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hex.setHint("#RRGGBB");
        col.addView(hex);

        final Runnable update = new Runnable() {
            @Override
            public void run() {
                int c = Color.HSVToColor(hsv);
                preview.setBackgroundColor(c);
                busy[0] = true;
                hex.setText(String.format("#%06X", c & 0xFFFFFF));
                busy[0] = false;
            }
        };

        SeekBar.OnSeekBarChangeListener l = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (!fromUser) return;
                hsv[0] = sh.getProgress();
                hsv[1] = ss.getProgress() / 100f;
                hsv[2] = sv.getProgress() / 100f;
                update.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) { }

            @Override
            public void onStopTrackingTouch(SeekBar s) { }
        };
        sh.setOnSeekBarChangeListener(l);
        ss.setOnSeekBarChangeListener(l);
        sv.setOnSeekBarChangeListener(l);

        hex.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a1, int b, int c) { }

            @Override
            public void onTextChanged(CharSequence s, int a1, int b, int c) { }

            @Override
            public void afterTextChanged(Editable s) {
                if (busy[0]) return;
                java.util.regex.Matcher m = HEX.matcher(s.toString().trim());
                if (!m.matches()) return;
                int c = Color.parseColor("#" + m.group(1));
                Color.colorToHSV(c, hsv);
                preview.setBackgroundColor(c);
                sh.setProgress((int) hsv[0]);
                ss.setProgress((int) (hsv[1] * 100));
                sv.setProgress((int) (hsv[2] * 100));
            }
        });

        update.run();

        new AlertDialog.Builder(a)
                .setTitle(R.string.color_title)
                .setView(col)
                .setPositiveButton(R.string.insert, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        cb.onColor(Color.HSVToColor(hsv) & 0xFFFFFF);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static SeekBar bar(Activity a, LinearLayout col, String label, int max, int progress) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView t = new TextView(a);
        t.setText(label);
        row.addView(t, new LinearLayout.LayoutParams(
                (int) (24 * a.getResources().getDisplayMetrics().density), LinearLayout.LayoutParams.WRAP_CONTENT));
        SeekBar s = new SeekBar(a);
        s.setMax(max);
        s.setProgress(progress);
        row.addView(s, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(row);
        return s;
    }
}
