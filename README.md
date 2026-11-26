# TextEdit – app Android sửa file văn bản

Mỗi lần push lên `main`, GitHub Actions tự build APK, tạo tag `v1.0.N` và Release có sẵn file APK (tab **Releases**).

## Soạn thảo
- **Nhiều thẻ (tab)**: mở nhiều file cùng lúc (chọn nhiều file một lượt), chuyển thẻ bằng cách chạm; thẻ có dấu `●` khi chưa lưu.
- **Hoàn tác / Làm lại không giới hạn**: lịch sử lưu theo từng thay đổi (chỉ phần chữ bị đổi), nên không giới hạn số bước và không tốn RAM theo cỡ file. Các lần gõ liên tiếp được gộp thành một bước.
- **Tìm / Thay thế**: tìm tiếp / lùi, phân biệt hoa thường, biểu thức chính quy (Regex), thay một chỗ, thay tất cả (hỗ trợ `$1` khi bật Regex). **Nhảy tới dòng** (`dòng` hoặc `dòng:cột`).
- **Bảng mã**: tự nhận diện BOM, UTF-8, UTF-16, ISO-2022-JP, Shift_JIS, EUC-JP, GBK/GB18030, Big5, EUC-KR, Windows-125x… (tối đa 46 bảng mã tùy máy). Chạm vào tên bảng mã ở thanh trạng thái để **mở lại với bảng mã khác**, **lưu với bảng mã khác** hoặc bật/tắt BOM.
- **File lớn**: giải mã ở luồng nền, tô màu chỉ trong vùng đang nhìn thấy, số dòng tính lười. Giới hạn mở file: 12 MB.
- Thanh trạng thái: dòng/cột, ngôn ngữ cú pháp (chạm để đổi), bảng mã.

## Lập trình
- **Tô màu cú pháp**: 126 ngôn ngữ / định dạng, nhận theo đuôi hoặc tên file (có thể chọn thủ công). Bộ tô màu tự viết, chạy ở luồng nền, không dùng thư viện ngoài.
- **Tự thụt lề** dòng mới (thêm một cấp sau `{ ( [`, và sau `:` với Python/YAML…), **Thụt vào / Thụt ra** cho các dòng đang chọn (nút trên thanh công cụ, menu nhấn giữ, hoặc phím Tab / Shift+Tab). Độ rộng thụt lề chỉnh được (2–8).
- **Xem trước**: HTML, SVG, CSS, Markdown, AsciiDoc (bộ hiển thị Markdown/AsciiDoc tự viết, hỗ trợ phần cú pháp thông dụng, không phải toàn bộ đặc tả).
- **Chọn màu Hex**: chèn mã `#RRGGBB`; nếu con trỏ đang nằm trên một mã màu thì mở đúng màu đó và thay thế.
- **Chạy online**: 38 ngôn ngữ ánh xạ sang Judge0 (mặc định `https://ce.judge0.com`, đổi được trong Cài đặt, có thể nhập token). Mã nguồn được gửi lên máy chủ đó, nên app hỏi đồng ý trước lần đầu. JavaScript chạy ngay trong app, HTML/CSS/Markdown/AsciiDoc thì xem trước.

## Kết nối
- **Google Drive / Dropbox / OneDrive**: qua trình chọn file của Android (hệ thống tài liệu SAF). Cần cài app đám mây tương ứng trên máy; app không tự đăng nhập đám mây.
- **FTP / FTPS / SFTP**: nhập máy chủ, cổng, tài khoản, mật khẩu; duyệt thư mục, mở và lưu file. SFTP ghi nhớ dấu vân tay khóa máy chủ ở lần kết nối đầu tiên.
- **GitHub / GitLab**: dùng token cá nhân (Personal Access Token), duyệt kho, mở file và lưu thành commit (có hỏi nội dung commit). Mật khẩu và token chỉ được lưu trên máy khi bạn tích “Nhớ trên thiết bị này” (lưu dạng thường trong bộ nhớ riêng của app).
- **File hệ thống (root)**: dùng `su`; chỉ hoạt động trên máy đã root.

## Giao diện
- **Sáng / Tối / Theo hệ thống (Mobile)**, phông chữ (có sẵn hoặc file .ttf/.otf), cỡ chữ, màu chữ, hình nền (màu / ảnh / video từ file hoặc liên kết), cắt dòng, số dòng, thanh công cụ mini, ngôn ngữ giao diện (tiếng Anh, tiếng Việt).
- **Cuộn hai chiều**: khi tắt “Cắt dòng”, vuốt cả dọc lẫn ngang.
- **Phím tắt (bàn phím ngoài)**: `Ctrl+S` lưu, `Ctrl+Shift+S` lưu thành, `Ctrl+O` mở, `Ctrl+N` thẻ mới, `Ctrl+W` đóng thẻ, `Ctrl+F` tìm, `Ctrl+H` thay thế, `Ctrl+G` tới dòng, `Ctrl+Z` hoàn tác, `Ctrl+Y` / `Ctrl+Shift+Z` làm lại, `Ctrl+Tab` / `Ctrl+PageDown` thẻ kế, `Ctrl+PageUp` thẻ trước, `F3` / `Shift+F3` tìm tiếp / lùi, `Tab` / `Shift+Tab` thụt lề, `Esc` đóng khung tìm.
- **Nhấn giữ** trong văn bản: menu mini (Dán, Chọn tất cả; Sao chép/Cắt khi đang chọn; Thụt vào/ra khi dòng có chữ).

## Ngôn ngữ giao diện
Danh sách chọn gồm mọi ngôn ngữ ISO. Hiện có bản dịch tiếng Anh (mặc định) và tiếng Việt;
ngôn ngữ khác sẽ hiện tiếng Anh. Muốn thêm ngôn ngữ, tạo `app/src/main/res/values-<mã>/strings.xml`
(sao chép từ `values/strings.xml` rồi dịch).

## Build
- GitHub: push lên `main` hoặc chạy tay workflow **Build APK** ở tab Actions.
- Android Studio: mở thư mục này, **Build > Build APK(s)**.
