# TextEdit – app Android sửa file văn bản

Mỗi lần push lên `main`, GitHub Actions tự build APK, tạo tag `v1.0.N` và Release có sẵn file APK (tab **Releases**).

## Tính năng
- Tạo mới, mở, lưu, lưu thành (UTF-8), cảnh báo thay đổi chưa lưu.
- **Thanh công cụ mini**: Mở, Dán, Sao chép, Hoàn tác, Làm lại, Tìm, Lưu, Thực hiện, Đóng file.
- **Nhấn giữ** trong văn bản: hiện menu mini (Dán, Chọn tất cả; Sao chép/Cắt khi đang chọn; Tăng/Giảm thụt lề khi dòng có chữ).
- **Thực hiện**: file `.html` xem trước trong WebView; file `.js` chạy trong WebView và hiện kết quả `console.log`. Loại file khác sẽ hỏi chạy như HTML hay JavaScript.
- **Cài đặt**: font (có sẵn hoặc nhập file .ttf/.otf), cỡ chữ, màu chữ, hình nền (màu / ảnh từ file hoặc link https / video từ file hoặc link), độ mờ nền sau chữ, ngôn ngữ, bật/tắt thanh công cụ, cắt dòng, số dòng.

## Ngôn ngữ
Danh sách chọn gồm mọi ngôn ngữ ISO. Giao diện hiện có bản dịch tiếng Anh (mặc định) và tiếng Việt;
ngôn ngữ khác sẽ hiện tiếng Anh. Muốn thêm ngôn ngữ, tạo `app/src/main/res/values-<mã>/strings.xml`
(sao chép từ `values/strings.xml` rồi dịch).

## Build
- GitHub: push lên `main` hoặc chạy tay workflow **Build APK** ở tab Actions.
- Android Studio: mở thư mục này, **Build > Build APK(s)**.
