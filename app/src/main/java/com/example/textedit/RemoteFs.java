package com.example.textedit;

import java.util.List;

/** Hệ thống file trên máy chủ từ xa. Mỗi thao tác tự mở và đóng kết nối nên không bị treo khi nhàn rỗi. */
interface RemoteFs {
    List<RemoteEntry> list(String dir) throws Exception;

    byte[] read(String path) throws Exception;

    void write(String path, byte[] data, String message) throws Exception;

    /** Có cần nhập nội dung commit khi lưu (Git) hay không. */
    boolean wantsMessage();

    /** Thư mục bắt đầu duyệt. */
    String start();
}
