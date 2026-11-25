# TextEdit – app Android sửa file văn bản

Tính năng: tạo mới, mở, lưu, lưu thành (UTF-8), cảnh báo khi có thay đổi chưa lưu,
và có thể mở file `text/*` từ trình quản lý file. Không cần xin quyền bộ nhớ
(dùng Storage Access Framework), không có thư viện ngoài.

## Cách 1: Build bằng GitHub (không cần cài gì)
1. Tạo repo GitHub mới, upload toàn bộ thư mục này (giữ cả thư mục `.github`).
2. Vào tab **Actions** → workflow **Build APK** → chạy (hoặc đợi tự chạy sau khi push).
3. Khi xong, tải artifact **TextEdit-debug-apk** → giải nén ra `app-debug.apk`.
4. Chép sang điện thoại, cho phép "cài từ nguồn không rõ" rồi cài.

## Cách 2: Build bằng Android Studio
Mở thư mục này bằng Android Studio → **Build > Build APK(s)**.
File nằm ở `app/build/outputs/apk/debug/app-debug.apk`.

## Cách 3: Dòng lệnh
Cần JDK 17, Android SDK (platform 34) và Gradle 8.7+:
`gradle assembleDebug`
