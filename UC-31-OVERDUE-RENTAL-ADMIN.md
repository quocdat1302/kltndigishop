# UC-31: Nhắc quá hạn trả máy cho Admin

## 📋 Tóm tắt thay đổi

Đã thêm tính năng để Admin được cảnh báo khi khách hàng quá hạn trả máy thuê.

### 1. **Entity Changes**

#### [Order.java](/d:/Hoctap/digishop/digishop/src/main/java/com/khoaluan/digishop/entity/Order.java)
- ✅ Thêm field `overdueReminderSentAt: Instant` - theo dõi lần gửi notification quá hạn cuối cùng

#### [NotificationType.java](/d:/Hoctap/digishop/digishop/src/main/java/com/khoaluan/digishop/entity/NotificationType.java)
- ✅ Thêm `OVERDUE_RENTAL` - type notification cho Admin về đơn quá hạn

### 2. **Repository Changes**

#### [OrderRepository.java](/d:/Hoctap/digishop/digishop/src/main/java/com/khoaluan/digishop/repository/OrderRepository.java)
- ✅ Thêm method `findByOrderTypeAndStatusAndRentalEndDateBeforeAndOverdueReminderSentAtIsNull()`
  - Tìm đơn thuê DELIVERED có rentalEndDate < hôm nay và chưa gửi reminder

### 3. **Service Changes**

#### [EmailService.java](/d:/Hoctap/digishop/digishop/src/main/java/com/khoaluan/digishop/service/EmailService.java)
- ✅ Thêm method `sendRentalOverdueReminderEmail()`
  - Email cảnh báo đỏ (màu #991b1b) với tiêu đề "CANH BAO"
  - Hiển thị thông tin đơn, sản phẩm, và lý do quá hạn

#### [RentalReminderScheduler.java](/d:/Hoctap/digishop/digishop/src/main/java/com/khoaluan/digishop/service/RentalReminderScheduler.java)
- ✅ Thêm method `sendOverdueReminders()` - chạy mỗi ngày lúc **10:00 AM (VN)**
- ✅ Gửi notification cho **TẤT CẢ Admin** bao gồm:
  - Mã đơn, tên khách, số điện thoại
  - Số ngày quá hạn
  - Số tiền cọc có thể bị trừ
- ✅ Đánh dấu `overdueReminderSentAt` để tránh gửi lặp

#### [ReportService.java](/d:/Hoctap/digishop/digishop/src/main/java/com/khoaluan/digishop/service/ReportService.java)
- ✅ Thêm method `getOverdueRentals()` - lấy danh sách đơn quá hạn
- ✅ Tính số ngày quá hạn tự động
- ✅ Sắp xếp theo số ngày quá hạn (nhiều nhất trước)

### 4. **DTO Changes**

#### [OverdueRentalDto.java](/d:/Hoctap/digishop/digishop/src/main/java/com/khoaluan/digishop/dto/OverdueRentalDto.java) - **NEW**
- Mã đơn, ID
- Thông tin khách (tên, điện thoại, email)
- Ngày hết hạn & số ngày quá hạn
- Tên sản phẩm, số lượng
- Tiền cọc
- Thời điểm gửi reminder lần cuối

### 5. **API Endpoint Changes**

#### [ReportController.java](/d:/Hoctap/digishop/digishop/src/main/java/com/khoaluan/digishop/controller/ReportController.java)
- ✅ Thêm endpoint `GET /api/admin/reports/overdue-rentals`
  - Chỉ admin có thể truy cập (`@PreAuthorize("hasRole('ADMIN')")`)
  - Trả về danh sách đơn thuê quá hạn
  - Sắp xếp theo số ngày quá hạn

## ⏰ Hoạt động Job

| Giờ | Job | Mô tả |
|-----|-----|-------|
| 09:00 AM | `sendDueTomorrowReminders()` | Nhắc trước 1 ngày hết hạn (UC-30) |
| 10:00 AM | `sendOverdueReminders()` | Nhắc quá hạn cho khách + Admin (UC-31) **MỚI** |

## 📧 Notification Admin

**Type:** `OVERDUE_RENTAL`

**Title:** `Đơn thuê quá hạn: [MÃ_ĐƠN]`

**Content:**
```
Khách [Tên Khách] ([Số ĐT]) quá hạn trả máy [Số ngày] ngày (hết hạn [Ngày hết hạn]). Cọc: [Tiền cọc] VND.
```

**Ví dụ:**
```
Khách Đạt Quốc (0365000132) quá hạn trả máy 4 ngày (hết hạn 01/08/2026). Cọc: 270,000 VND.
```

## 🔄 Quy trình khi có đơn quá hạn

1. **Lúc 10:00 AM mỗi ngày:**
   - Job chạy `sendOverdueReminders()`
   - Tìm các đơn có `rentalEndDate < hôm nay` và `overdueReminderSentAt IS NULL`

2. **Với mỗi đơn quá hạn:**
   - ✅ Gửi email cảnh báo cho khách hàng
   - ✅ Gửi notification cho khách hàng (trong app)
   - ✅ Gửi notification cho TẤT CẢ Admin (trong app)
   - ✅ Đánh dấu `overdueReminderSentAt = Instant.now()`

3. **Admin xem danh sách:**
   - Truy cập `/api/admin/reports/overdue-rentals`
   - Xem tất cả đơn quá hạn chưa trả
   - Thông tin bao gồm số ngày quá hạn, tiền cọc, liên hệ khách

## ✅ Testing

Để test chức năng này:
1. Tạo đơn thuê với rentalEndDate = hôm nay - 5 ngày
2. Để status = DELIVERED
3. Lúc 10:00 AM, job sẽ tự động gửi notification cho Admin
4. Hoặc call trực tiếp: `GET /api/admin/reports/overdue-rentals`

## 📝 Ghi chú

- Email được gửi **async** qua `@Async` của EmailService - không block job
- Notification được tạo ngay trong transaction
- Tránh gửi lặp bằng field `overdueReminderSentAt`
- Admin có thể thấy được tất cả đơn quá hạn trong một view
- Mỗi Admin sẽ nhận notification riêng (không phức tạp cơ sở dữ liệu)
