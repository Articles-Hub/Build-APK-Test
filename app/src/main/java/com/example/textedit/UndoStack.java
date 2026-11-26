package com.example.textedit;

import java.util.ArrayList;

/**
 * Lịch sử hoàn tác dựa trên thay đổi (delta): mỗi bước chỉ lưu đoạn văn bản bị thay thế,
 * nên không giới hạn số bước và không tốn bộ nhớ theo kích thước file.
 * Các lần gõ liên tiếp được gộp thành một bước.
 */
final class UndoStack {

    static final class Op {
        int start;
        String oldText;
        String newText;
        long time;

        int size() {
            return oldText.length() + newText.length();
        }
    }

    private static final long MERGE_MS = 800;
    private static final int MERGE_MAX = 4000;
    /** Giới hạn an toàn bộ nhớ (ký tự); vượt quá thì bỏ các bước cũ nhất. */
    private static final long MAX_CHARS = 64L * 1024 * 1024;

    private final ArrayList<Op> ops = new ArrayList<>();
    private int pos = 0;        // số bước đang được áp dụng
    private int savedPos = 0;   // giá trị pos tại lần lưu cuối (-1 nếu không còn khớp)
    private long total = 0;
    private boolean noMerge = true;

    void clear() {
        ops.clear();
        pos = 0;
        savedPos = 0;
        total = 0;
        noMerge = true;
    }

    void markSaved() {
        savedPos = pos;
        noMerge = true;
    }

    boolean isDirty() {
        return pos != savedPos;
    }

    /**
     * Lấy "dấu" của trạng thái hiện tại trước khi lưu bất đồng bộ, và chặn gộp thêm vào bước cuối
     * để nội dung đã chụp không bị đổi. Sau khi lưu xong gọi markSavedAt(dấu).
     */
    Object token() {
        noMerge = true;
        return pos == 0 ? null : ops.get(pos - 1);
    }

    void markSavedAt(Object token) {
        noMerge = true;
        if (token == null) {
            savedPos = 0;
            return;
        }
        for (int i = ops.size() - 1; i >= 0; i--) {
            if (ops.get(i) == token) {
                savedPos = i + 1;
                return;
            }
        }
        savedPos = -1; // bước đã bị loại bỏ: không còn khớp với bản đã lưu
    }

    boolean canUndo() {
        return pos > 0;
    }

    boolean canRedo() {
        return pos < ops.size();
    }

    int size() {
        return ops.size();
    }

    void record(int start, String oldText, String newText, long now) {
        if (oldText.equals(newText)) return;

        if (pos < ops.size()) { // bỏ nhánh "làm lại" cũ
            for (int i = ops.size() - 1; i >= pos; i--) {
                total -= ops.remove(i).size();
            }
            if (savedPos > pos) savedPos = -1;
        }

        if (pos > 0 && !noMerge && tryMerge(start, oldText, newText, now)) return;
        noMerge = false;

        Op op = new Op();
        op.start = start;
        op.oldText = oldText;
        op.newText = newText;
        op.time = now;
        ops.add(op);
        pos++;
        total += op.size();

        while (total > MAX_CHARS && ops.size() > 1) {
            total -= ops.remove(0).size();
            pos--;
            if (savedPos >= 0) savedPos--;
            if (savedPos < 0) savedPos = -1;
        }
    }

    private boolean tryMerge(int ns, String no, String nn, long now) {
        Op last = ops.get(pos - 1);
        if (now - last.time > MERGE_MS) return false;
        if (nn.indexOf('\n') >= 0 || no.indexOf('\n') >= 0) return false;
        if (last.newText.length() > MERGE_MAX) return false;

        int ls = last.start;
        int le = ls + last.newText.length();
        String merged;
        int mStart;
        String mOld;
        if (ns >= ls && ns + no.length() <= le) {
            // Thay đổi nằm trong vùng chữ vừa gõ.
            int a = ns - ls;
            merged = last.newText.substring(0, a) + nn + last.newText.substring(a + no.length());
            mStart = ls;
            mOld = last.oldText;
        } else if (ns + no.length() == ls) {
            // Xóa lùi hoặc chèn ngay trước vùng vừa gõ.
            merged = nn + last.newText;
            mStart = ns;
            mOld = no + last.oldText;
        } else {
            return false;
        }
        total += (long) (mOld.length() + merged.length()) - last.size();
        last.start = mStart;
        last.oldText = mOld;
        last.newText = merged;
        last.time = now;
        return true;
    }

    /** Trả về bước cần hoàn tác (thay newText bằng oldText), hoặc null. */
    Op undo() {
        if (pos == 0) return null;
        noMerge = true;
        return ops.get(--pos);
    }

    /** Trả về bước cần làm lại (thay oldText bằng newText), hoặc null. */
    Op redo() {
        if (pos >= ops.size()) return null;
        noMerge = true;
        return ops.get(pos++);
    }
}
