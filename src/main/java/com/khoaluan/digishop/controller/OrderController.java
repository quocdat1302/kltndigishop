package com.khoaluan.digishop.controller;

import com.khoaluan.digishop.dto.CheckoutPurchaseRequest;
import com.khoaluan.digishop.dto.CheckoutRentalRequest;
import com.khoaluan.digishop.dto.DeductDepositRequest;
import com.khoaluan.digishop.dto.ExtendRentalRequest;
import com.khoaluan.digishop.dto.InspectReturnRequest;
import com.khoaluan.digishop.dto.MarkDeliveredRequest;
import com.khoaluan.digishop.dto.DayAvailabilityDto;
import com.khoaluan.digishop.dto.OrderDto;
import com.khoaluan.digishop.dto.ProductStockBreakdownDto;
import com.khoaluan.digishop.dto.RejectReturnRequest;
import com.khoaluan.digishop.dto.RentalCalendarEntryDto;
import com.khoaluan.digishop.dto.RentalInventoryEntryDto;
import com.khoaluan.digishop.dto.RequestReturnRequest;
import com.khoaluan.digishop.dto.RentalContractDto;
import com.khoaluan.digishop.dto.SignRentalContractRequest;
import com.khoaluan.digishop.dto.UpdateOrderStatusRequest;
import com.khoaluan.digishop.entity.Order;
import com.khoaluan.digishop.entity.OrderType;
import com.khoaluan.digishop.entity.User;
import com.khoaluan.digishop.service.InvoiceService;
import com.khoaluan.digishop.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final InvoiceService invoiceService;

    // ------------------------------------------------------------------
    // Trang đặt mua (Purchase checkout)
    // ------------------------------------------------------------------

    @PostMapping("/purchase")
    public ResponseEntity<OrderDto> checkoutPurchase(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CheckoutPurchaseRequest req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.checkoutPurchase(user, req));
    }

    // ------------------------------------------------------------------
    // Trang đặt thuê (Rental checkout)
    // ------------------------------------------------------------------

    @PostMapping("/rental")
    public ResponseEntity<OrderDto> checkoutRental(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CheckoutRentalRequest req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.checkoutRental(user, req));
    }

    // ------------------------------------------------------------------
    // Hợp đồng thuê điện tử — xem trước / ký / xem lại sau khi ký
    // ------------------------------------------------------------------

    @GetMapping("/{id}/rental-contract/preview")
    public String getContractPreview(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return orderService.getContractPreview(user, id);
    }

    @PostMapping("/{id}/rental-contract/sign")
    public OrderDto signRentalContract(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody SignRentalContractRequest req
    ) {
        return orderService.signRentalContract(user, id, req.signatureDataUrl());
    }

    @GetMapping("/{id}/rental-contract")
    public RentalContractDto getRentalContract(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return orderService.getRentalContract(user, id);
    }

    /** Lịch trống công khai của 1 sản phẩm — khách xem trước khi đặt để biết ngày nào còn máy. */
    @GetMapping("/products/{productId}/availability")
    public List<DayAvailabilityDto> getProductAvailability(
            @PathVariable Long productId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return orderService.getProductAvailability(productId, from, to);
    }

    // ------------------------------------------------------------------
    // Lịch sử đơn hàng của khách hàng
    // ------------------------------------------------------------------

    @GetMapping
    public List<OrderDto> getMyOrders(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) OrderType type
    ) {
        return orderService.getMyOrders(user, type);
    }

    @GetMapping("/{orderId}")
    public OrderDto getMyOrder(
            @AuthenticationPrincipal User user,
            @PathVariable Long orderId
    ) {
        return orderService.getMyOrderById(user, orderId);
    }

    @PatchMapping("/{orderId}/cancel")
    public OrderDto cancelMyOrder(
            @AuthenticationPrincipal User user,
            @PathVariable Long orderId
    ) {
        return orderService.cancelMyOrder(user, orderId);
    }

    /** Khách tự gia hạn thời gian thuê khi đang trong thời gian thuê (DELIVERED). */
    @PutMapping("/{orderId}/extend")
    public OrderDto extendRental(
            @AuthenticationPrincipal User user,
            @PathVariable Long orderId,
            @Valid @RequestBody ExtendRentalRequest req
    ) {
        return orderService.extendRental(user, orderId, req.newEndDate());
    }

    // ------------------------------------------------------------------
    // Đổi trả đơn mua (UC-25)
    // ------------------------------------------------------------------

    @PatchMapping({"/{orderId}/return", "/{orderId}/request-return"})
    public OrderDto requestReturn(
            @AuthenticationPrincipal User user,
            @PathVariable Long orderId,
            @Valid @RequestBody RequestReturnRequest req
    ) {
        return orderService.requestReturn(user, orderId, req.reason(), req.imageUrls());
    }

    @PatchMapping("/admin/{orderId}/return/approve")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public OrderDto approveReturn(@PathVariable Long orderId) {
        return orderService.approveReturn(orderId);
    }

    @PatchMapping("/admin/{orderId}/return/reject")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public OrderDto rejectReturn(
            @PathVariable Long orderId,
            @Valid @RequestBody RejectReturnRequest req
    ) {
        return orderService.rejectReturn(orderId, req.reason());
    }

    // ------------------------------------------------------------------
    // Xuất hoá đơn PDF (UC-27)
    // ------------------------------------------------------------------

    @GetMapping("/{orderId}/invoice")
    public ResponseEntity<byte[]> downloadMyInvoice(
            @AuthenticationPrincipal User user,
            @PathVariable Long orderId
    ) {
        Order order = orderService.getMyOrderEntity(user, orderId);
        return buildInvoiceResponse(order);
    }

    @GetMapping("/admin/{orderId}/invoice")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ResponseEntity<byte[]> downloadInvoiceAsAdmin(@PathVariable Long orderId) {
        Order order = orderService.getOrderEntity(orderId);
        return buildInvoiceResponse(order);
    }

    private ResponseEntity<byte[]> buildInvoiceResponse(Order order) {
        byte[] pdf = invoiceService.generateInvoice(order);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.attachment().filename("hoa-don-" + order.getOrderCode() + ".pdf").build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    // ------------------------------------------------------------------
    // Quản trị viên
    // ------------------------------------------------------------------

    @GetMapping("/admin/all")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public List<OrderDto> getAllOrders(@RequestParam(required = false) OrderType type) {
        return orderService.getAllOrders(type);
    }

    /** Admin xem hợp đồng đã ký của bất kỳ đơn thuê nào — để đối chiếu tình trạng máy/điều khoản trước khi xác nhận, giao máy. */
    @GetMapping("/admin/{orderId}/rental-contract")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public RentalContractDto getRentalContractAsAdmin(@PathVariable Long orderId) {
        return orderService.getRentalContractForAdmin(orderId);
    }

    @PatchMapping("/admin/{orderId}/status")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public OrderDto updateOrderStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody UpdateOrderStatusRequest req
    ) {
        return orderService.updateOrderStatus(orderId, req.status());
    }

    // ------------------------------------------------------------------
    // Quy trình thuê (rental lifecycle): cọc -> giao máy -> trả máy -> kiểm tra
    // ------------------------------------------------------------------

    @PatchMapping("/admin/{orderId}/rental/deposit-paid")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public OrderDto markDepositPaid(@PathVariable Long orderId) {
        return orderService.markDepositPaid(orderId);
    }

    @PatchMapping("/admin/{orderId}/rental/delivered")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public OrderDto markDelivered(
            @PathVariable Long orderId,
            @Valid @RequestBody MarkDeliveredRequest req
    ) {
        return orderService.markDelivered(orderId, req.conditionNote());
    }

    @PatchMapping("/admin/{orderId}/rental/returned")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public OrderDto markRentalReturned(@PathVariable Long orderId) {
        return orderService.markRentalReturned(orderId);
    }

    @PatchMapping("/admin/{orderId}/rental/inspect")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public OrderDto inspectRentalReturn(
            @PathVariable Long orderId,
            @Valid @RequestBody InspectReturnRequest req
    ) {
        return orderService.inspectRentalReturn(orderId, req.inspectionNote());
    }

    /** Bước 8a: hoàn đủ cọc, không phát sinh hư hỏng/trễ hạn. */
    @PutMapping("/admin/{orderId}/rental/deposit/refund")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public OrderDto refundDeposit(@PathVariable Long orderId) {
        return orderService.refundDeposit(orderId);
    }

    /** Bước 8b: trừ một phần cọc do hư hỏng/trễ hạn. */
    @PutMapping("/admin/{orderId}/rental/deposit/deduct")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public OrderDto deductDeposit(
            @PathVariable Long orderId,
            @Valid @RequestBody DeductDepositRequest req
    ) {
        return orderService.deductDeposit(orderId, req.damageAmount(), req.disputeReason());
    }

    /** Lịch thuê tổng quan theo sản phẩm/ngày, gộp sẵn ở backend cho khoảng [from, to]. */
    @GetMapping("/admin/rental-calendar")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public List<RentalCalendarEntryDto> getRentalCalendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return orderService.getRentalCalendar(from, to);
    }

    /** Tách tồn kho từng sản phẩm thành: tổng kho / đang giữ chỗ cho thuê tương lai / có thể bán ngay. */
    @GetMapping("/admin/stock-breakdown")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public List<ProductStockBreakdownDto> getStockBreakdown() {
        return orderService.getStockBreakdownForAllProducts();
    }

    /** Tồn kho thuê tại 1 ngày cụ thể cho mọi sản phẩm: tổng kho / đang thuê / còn trống / buổi đã đặt. */
    @GetMapping("/admin/rental-inventory")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public List<RentalInventoryEntryDto> getRentalInventory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return orderService.getRentalInventory(date);
    }
}