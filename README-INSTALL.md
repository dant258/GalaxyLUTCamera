# Cách tạo APK để cài như ứng dụng Android bình thường

Hiện môi trường tạo mã nguồn không có Android SDK/Gradle nên chưa thể xuất APK đã biên dịch và kiểm thử ngay tại đây. Dự án đã kèm GitHub Actions để build APK tự động trên GitHub.

## Cách dễ nhất (cần tài khoản GitHub)
1. Tạo repository GitHub mới, ví dụ `GalaxyLUTCamera`.
2. Upload toàn bộ nội dung thư mục dự án này lên repository (giữ nguyên thư mục `.github/workflows`).
3. Mở tab **Actions** của repository.
4. Chọn workflow **Build Galaxy LUT Camera APK** rồi bấm **Run workflow** (hoặc push lên nhánh `main` để workflow tự chạy).
5. Khi workflow hoàn tất, mở lần chạy đó, kéo xuống **Artifacts**, tải `GalaxyLUTCamera-debug-apk`.
6. Giải nén ZIP, lấy `app-debug.apk`, gửi sang Galaxy S26 và mở file để cài. Nếu Android hỏi, cho phép trình cài đặt/file manager được cài ứng dụng không rõ nguồn gốc.

## Lưu ý
- Đây là APK debug, chưa ký phát hành Play Store. Chỉ cài bản do chính repository của bạn build.
- Cấp quyền Camera khi mở app.
- Bản Alpha chưa bảo đảm truy cập được đủ 3 camera vật lý; phải kiểm tra trên SM-S942B.
- Không tích hợp vào Samsung Camera/Good Lock. Đây là ứng dụng camera độc lập do giới hạn API công khai.
