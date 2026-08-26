package com.khoaluan.digishop.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mã đơn hiển thị cho khách, vd DH2507080001. */
    @Column(name = "order_code", nullable = false, unique = true)
    private String orderCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** PURCHASE (đơn mua) hoặc RENTAL (đơn thuê). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false)
    private String recipientPhone;

    /** Bắt buộc với đơn mua và đơn thuê giao tận nơi; có thể để trống nếu thuê và khách chọn nhận tại shop. */
    @Column(name = "shipping_address")
    private String shippingAddress;

    /** Chỉ dùng khi orderType = RENTAL: khách nhận máy tại shop hay được giao tận nơi. */
    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_method")
    private FulfillmentMethod fulfillmentMethod;

    /** Snapshot địa điểm/hình thức nhận máy đã chọn (xem PickupLocation) — không đổi dù sau này địa
     *  điểm gốc bị admin sửa/xoá. pickupFee đã được cộng vào totalAmount lúc đặt. */
    @Column(name = "pickup_location_name")
    private String pickupLocationName;

    @Column(name = "pickup_fee")
    private java.math.BigDecimal pickupFee;

    private String note;

    /** Chỉ dùng khi orderType = RENTAL: khoảng ngày thuê chung cho cả đơn. */
    @Column(name = "rental_start_date")
    private LocalDate rentalStartDate;

    @Column(name = "rental_end_date")
    private LocalDate rentalEndDate;

    /**
     * Tiền phạt trễ hạn tích luỹ đến lần job "sendOverdueReminders" gần nhất (cập nhật lại mỗi ngày,
     * miễn đơn còn ở trạng thái DELIVERED và chưa trả máy). Đơn giá phạt/ngày = subtotalAmount chia cho
     * số ngày thuê dự kiến ban đầu (tức đúng bằng 1 ngày thuê thêm cho mỗi ngày trễ).
     */
    @Column(name = "late_fee_amount", precision = 15, scale = 2)
    private BigDecimal lateFeeAmount;

    @Column(name = "rental_days")
    private Integer rentalDays;

    /** Tổng tiền hàng/tiền thuê (chưa gồm cọc, chưa trừ giảm giá). */
    @Column(name = "subtotal_amount", nullable = false)
    private BigDecimal subtotalAmount;

    /** Mã khuyến mãi đã áp dụng (nếu có). */
    @Column(name = "promotion_code")
    private String promotionCode;

    /** Số tiền được giảm nhờ mã khuyến mãi. */
    @Column(name = "discount_amount", nullable = false)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /** Số tiền được giảm nhờ hạng "khách hàng thân thiết" (tự động, cộng dồn với mã khuyến mãi nếu có). */
    @Column(name = "loyalty_discount_amount", nullable = false)
    @Builder.Default
    private BigDecimal loyaltyDiscountAmount = BigDecimal.ZERO;

    /** Tiền cọc, chỉ áp dụng cho đơn thuê. */
    @Column(name = "deposit_amount", nullable = false)
    private BigDecimal depositAmount;

    /** Tổng phải thanh toán = subtotalAmount - discountAmount + depositAmount. */
    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    /** Thời điểm đơn chuyển sang COMPLETED, dùng để tính thời hạn đổi trả. */
    @Column(name = "completed_at")
    private Instant completedAt;

    /** Lý do khách yêu cầu đổi trả (chỉ áp dụng đơn mua). */
    @Column(name = "return_reason")
    private String returnReason;

    /** Ảnh bằng chứng khách gửi kèm (vd: máy hư, trầy xước) — nhiều URL nối bằng dấu phẩy. */
    @Column(name = "return_image_urls", columnDefinition = "TEXT")
    private String returnImageUrls;

    @Column(name = "return_requested_at")
    private Instant returnRequestedAt;

    /** Lý do admin từ chối yêu cầu đổi trả (nếu có). */
    @Column(name = "return_reject_reason")
    private String returnRejectReason;

    @Column(name = "returned_at")
    private Instant returnedAt;

    /** Số tiền đã hoàn khi đổi trả được duyệt (đơn mua) hoặc hoàn cọc (đơn thuê). */
    @Column(name = "refund_amount")
    private BigDecimal refundAmount;

    // ---------------------------------------------------------------
    // Vòng đời đơn thuê (rental lifecycle) — chỉ có giá trị khi orderType = RENTAL.
    // ---------------------------------------------------------------

    /** Thời điểm khách đóng cọc (bước DEPOSIT_PAID). */
    @Column(name = "deposit_paid_at")
    private Instant depositPaidAt;

    /** Thời điểm giao thiết bị cho khách (bước DELIVERED). */
    @Column(name = "delivered_at")
    private Instant deliveredAt;

    /** Ghi chú/checklist tình trạng máy lúc giao cho khách. */
    @Column(name = "delivery_condition_note", columnDefinition = "TEXT")
    private String deliveryConditionNote;

    /** Thời điểm nhân viên kho kiểm tra tình trạng máy lúc nhận lại. */
    @Column(name = "inspected_at")
    private Instant inspectedAt;

    /** Ghi chú tình trạng máy lúc kiểm tra nhận lại, đối chiếu với lúc giao. */
    @Column(name = "inspection_note", columnDefinition = "TEXT")
    private String inspectionNote;

    /** Số tiền bị trừ vào cọc do hư hỏng/trễ hạn (0 nếu không tranh chấp). */
    @Column(name = "damage_amount")
    @Builder.Default
    private BigDecimal damageAmount = BigDecimal.ZERO;

    /** Lý do tranh chấp (hư hỏng/trễ hạn...) khi status = DISPUTED. */
    @Column(name = "dispute_reason")
    private String disputeReason;

    /** Thời điểm đã gửi email nhắc hạn trả máy — tránh gửi lặp lại nếu job chạy nhiều lần cùng ngày. */
    @Column(name = "due_reminder_sent_at")
    private Instant dueReminderSentAt;

    /** Thời điểm đã gửi email nhắc quá hạn trả máy — tránh gửi lặp lại nếu job chạy nhiều lần cùng ngày. */
    @Column(name = "overdue_reminder_sent_at")
    private Instant overdueReminderSentAt;

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    /** Phụ kiện bổ sung đã chọn (hoặc đi kèm miễn phí) cho đơn thuê này — xem OrderAddon. */
    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderAddon> addons = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}