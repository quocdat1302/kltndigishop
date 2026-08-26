package com.khoaluan.digishop.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OverdueRentalDto {
    /** Mã đơn hiển thị cho khách. */
    private String orderCode;

    /** ID đơn hàng. */
    private Long orderId;

    /** Tên khách hàng. */
    private String customerName;

    /** Số điện thoại khách hàng. */
    private String customerPhone;

    /** Email khách hàng. */
    private String customerEmail;

    /** Ngày hết hạn trả (đã qua). */
    @JsonFormat(pattern = "dd/MM/yyyy")
    private LocalDate rentalEndDate;

    /** Số ngày quá hạn. */
    private Long overdueDays;

    /** Tên sản phẩm được thuê. */
    private String productName;

    /** Số lượng sản phẩm. */
    private Integer quantity;

    /** Tiền cọc (có khả năng bị trừ do quá hạn). */
    private BigDecimal depositAmount;

    /** Lần gần nhất gửi reminder quá hạn. */
    @JsonFormat(pattern = "dd/MM/yyyy HH:mm:ss", timezone = "Asia/Ho_Chi_Minh")
    private java.time.Instant overdueReminderSentAt;

    /** Tiền phạt trễ hạn tạm tính (luỹ kế theo số ngày trễ). */
    private BigDecimal lateFeeAmount;
}