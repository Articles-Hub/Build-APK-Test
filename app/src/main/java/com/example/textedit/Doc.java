package com.example.textedit;

import java.nio.charset.Charset;

/** Một tài liệu đang mở trong một thẻ (tab). */
final class Doc {
    Backend backend;                 // null nếu chưa lưu ở đâu cả
    String name;                     // tên hiển thị khi chưa có backend
    String text = "";                // nội dung (chỉ đáng tin khi thẻ không phải thẻ đang mở)
    Charset charset = Charset.forName("UTF-8");
    boolean bom;
    boolean encodingChanged;         // đổi bảng mã/BOM nhưng chưa lưu
    int selStart;
    int selEnd;
    int scrollY;
    String langOverride;             // tên ngôn ngữ chọn thủ công
    final UndoStack undo = new UndoStack();

    boolean dirty() {
        return undo.isDirty() || encodingChanged;
    }

    String title(String untitled) {
        if (backend != null) {
            String n = backend.name();
            if (n != null && !n.isEmpty()) return n;
        }
        return (name != null && !name.isEmpty()) ? name : untitled;
    }

    String fileName() {
        if (backend != null) {
            String n = backend.name();
            if (n != null) return n;
        }
        return name == null ? "" : name;
    }

    Lang lang() {
        if (langOverride != null) return Langs.byName(langOverride);
        return Langs.forFile(fileName());
    }

    String encodingLabel() {
        String n = charset.name();
        return bom ? n + " BOM" : n;
    }
}
