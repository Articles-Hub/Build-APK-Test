package com.example.textedit;

import java.util.HashSet;
import java.util.Locale;

/** Mô tả một ngôn ngữ để tô màu cú pháp. */
final class Lang {
    static final int GENERIC = 0, HTML = 1, CSS = 2, MARKDOWN = 3, DIFF = 4, INI = 5, YAML = 6, LOG = 7;

    final String name;
    final int kind;

    final HashSet<String> kw = new HashSet<>();
    boolean ci;                       // từ khóa không phân biệt hoa thường
    String[] lineC = new String[0];   // ký hiệu chú thích một dòng
    String blockS, blockE;            // chú thích nhiều dòng
    String quotes = "\"'";            // ký tự mở/đóng chuỗi
    boolean triple;                   // chuỗi ba dấu nháy kiểu Python
    boolean charLit;                  // ký tự 'x' và nhãn 'a (Rust, OCaml)
    boolean preproc;                  // #include, #define
    boolean dollar;                   // biến $name (shell, perl, php)
    boolean atAttr;                   // @name là biến (Ruby) thay vì chú giải
    boolean backslashKw;              // \command (LaTeX)
    boolean keyStr;                   // chuỗi đứng trước ':' là khóa (JSON)
    boolean types;                    // từ viết hoa chữ đầu là tên kiểu
    boolean dashIdent;                // tên có dấu '-' (lisp, css)
    boolean xml;                      // HTML dạng XML (không nhúng script/style)
    boolean adoc;                     // AsciiDoc

    Lang(String name, int kind) {
        this.name = name;
        this.kind = kind;
    }

    Lang words(String list) {
        if (list == null) return this;
        for (String w : list.split("\\s+")) {
            if (!w.isEmpty()) kw.add(ci ? w.toLowerCase(Locale.ROOT) : w);
        }
        return this;
    }

    Lang ci() {
        ci = true;
        HashSet<String> old = new HashSet<>(kw);
        kw.clear();
        for (String w : old) kw.add(w.toLowerCase(Locale.ROOT));
        return this;
    }

    Lang line(String... c) { lineC = c; return this; }
    Lang block(String s, String e) { blockS = s; blockE = e; return this; }
    Lang quotes(String q) { quotes = q; return this; }
    Lang triple() { triple = true; return this; }
    Lang charLit() { charLit = true; return this; }
    Lang preproc() { preproc = true; return this; }
    Lang dollar() { dollar = true; return this; }
    Lang atAttr() { atAttr = true; return this; }
    Lang backslash() { backslashKw = true; return this; }
    Lang keyStr() { keyStr = true; return this; }
    Lang types() { types = true; return this; }
    Lang dash() { dashIdent = true; return this; }
}
