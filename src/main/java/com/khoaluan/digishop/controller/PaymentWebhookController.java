package com.khoaluan.digishop.controller;

import com.khoaluan.digishop.entity.Order;
import com.khoaluan.digishop.entity.OrderStatus;
import com.khoaluan.digishop.repository.OrderRepository;
import com.khoaluan.digishop.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/webhook")
public class PaymentWebhookController {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    // Đặt SEPAY_API_KEY qua biến môi trường, KHÔNG hard-code key thật vào đây.
    @Value("${app.sepay.api-key:}")
    private String sepayApiKey;

    // Mã đơn hàng thực tế có dạng "DM250728XXXX" (mua) hoặc "DT250728XXXX" (thuê)
    private static final Pattern ORDER_CODE_PATTERN = Pattern.compile("(DM|DT)\\d{10}");

    @PostMapping("/sepay")
    public ResponseEntity<?> handleSepayWebhook(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> payload
    ) {
        // 1. Xác thực request thực sự đến từ SePay bằng API Key
        String expected = "Apikey " + sepayApiKey;
        if (sepayApiKey.isBlank() || authHeader == null || !authHeader.equals(expected)) {
            log.warn("SePay webhook: unauthorized request");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false));
        }

        log.info("SePay webhook payload: {}", payload);

        String content = String.valueOf(payload.getOrDefault("content", "")).toUpperCase();
        Object amountObj = payload.get("transferAmount");

        Matcher matcher = ORDER_CODE_PATTERN.matcher(content);
        if (!matcher.find()) {
            log.warn("Không tìm thấy mã đơn hàng trong nội dung chuyển khoản: {}", content);
            // Vẫn trả 200 để SePay không thử gửi lại vô tận với giao dịch không liên quan
            return ResponseEntity.ok(Map.of("success", true));
        }

        String orderCode = matcher.group();
        Optional<Order> orderOpt = orderRepository.findByOrderCode(orderCode);
        if (orderOpt.isEmpty()) {
            log.warn("Không tìm thấy đơn hàng với mã {}", orderCode);
            return ResponseEntity.ok(Map.of("success", true));
        }

        Order order = orderOpt.get();
        if (order.getStatus() == OrderStatus.PENDING) {
            orderService.updateOrderStatus(order.getId(), OrderStatus.CONFIRMED);
            log.info("Đơn hàng {} đã nhận thanh toán {} và chuyển sang CONFIRMED", orderCode, amountObj);
        } else {
            log.info("Đơn hàng {} đang ở trạng thái {}, bỏ qua (không phải PENDING)", orderCode, order.getStatus());
        }

        // Luôn trả về 200 để SePay biết đã nhận thành công
        return ResponseEntity.ok(Map.of("success", true));
    }
}