package com.example.textedit;

/** Nơi lưu một tài liệu: thiết bị (SAF), hệ thống (root), máy chủ FTP/SFTP, hoặc Git. */
interface Backend {
    byte[] read() throws Exception;

    /** @param message nội dung commit (chỉ dùng cho Git; có thể null). */
    void write(byte[] data, String message) throws Exception;

    String name();

    boolean wantsMessage();
}
