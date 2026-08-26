package com.khoaluan.digishop.service;

import com.khoaluan.digishop.dto.AddToCartRequest;
import com.khoaluan.digishop.dto.CartItemDto;
import com.khoaluan.digishop.dto.CartSummaryDto;
import com.khoaluan.digishop.dto.UpdateCartItemRequest;
import com.khoaluan.digishop.entity.CartItem;
import com.khoaluan.digishop.entity.OrderType;
import com.khoaluan.digishop.entity.Product;
import com.khoaluan.digishop.entity.User;
import com.khoaluan.digishop.exception.ApiException;
import com.khoaluan.digishop.repository.CartItemRepository;
import com.khoaluan.digishop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public CartSummaryDto getCart(User user) {
        List<CartItem> items = cartItemRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

        List<CartItemDto> purchaseItems = items.stream()
                .filter(i -> i.getOrderType() == OrderType.PURCHASE)
                .map(this::toDto)
                .toList();

        List<CartItemDto> rentalItems = items.stream()
                .filter(i -> i.getOrderType() == OrderType.RENTAL)
                .map(this::toDto)
                .toList();

        BigDecimal purchaseSubtotal = purchaseItems.stream()
                .map(CartItemDto::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal rentalSubtotal = rentalItems.stream()
                .map(CartItemDto::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartSummaryDto(
                purchaseItems,
                rentalItems,
                items.size(),
                purchaseSubtotal,
                rentalSubtotal
        );
    }

    @Transactional
    public CartSummaryDto addToCart(User user, AddToCartRequest req) {
        Product product = productRepository.findById(req.productId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Sản phẩm không tồn tại"));

        if (Boolean.FALSE.equals(product.getIsAvailable())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRODUCT_UNAVAILABLE", "Sản phẩm hiện không khả dụng");
        }

        if (req.quantity() > product.getStockQuantity()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OUT_OF_STOCK",
                    "Chỉ còn " + product.getStockQuantity() + " sản phẩm trong kho");
        }

        if (req.orderType() == OrderType.RENTAL) {
            validateRentalDates(req.rentalStartDate(), req.rentalEndDate());

            CartItem item = CartItem.builder()
                    .user(user)
                    .product(product)
                    .orderType(OrderType.RENTAL)
                    .quantity(req.quantity())
                    .rentalStartDate(req.rentalStartDate())
                    .rentalEndDate(req.rentalEndDate())
                    .build();
            cartItemRepository.save(item);
        } else {
            // PURCHASE: gộp số lượng nếu sản phẩm đã có sẵn trong giỏ mua
            CartItem item = cartItemRepository
                    .findByUserIdAndProductIdAndOrderType(user.getId(), product.getId(), OrderType.PURCHASE)
                    .orElseGet(() -> CartItem.builder()
                            .user(user)
                            .product(product)
                            .orderType(OrderType.PURCHASE)
                            .quantity(0)
                            .build());

            int newQuantity = item.getQuantity() + req.quantity();
            if (newQuantity > product.getStockQuantity()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "OUT_OF_STOCK",
                        "Chỉ còn " + product.getStockQuantity() + " sản phẩm trong kho");
            }
            item.setQuantity(newQuantity);
            cartItemRepository.save(item);
        }

        return getCart(user);
    }

    @Transactional
    public CartSummaryDto updateCartItem(User user, Long itemId, UpdateCartItemRequest req) {
        CartItem item = cartItemRepository.findByIdAndUserId(itemId, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "Không tìm thấy sản phẩm trong giỏ hàng"));

        if (req.quantity() != null) {
            if (req.quantity() > item.getProduct().getStockQuantity()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "OUT_OF_STOCK",
                        "Chỉ còn " + item.getProduct().getStockQuantity() + " sản phẩm trong kho");
            }
            item.setQuantity(req.quantity());
        }

        if (item.getOrderType() == OrderType.RENTAL && (req.rentalStartDate() != null || req.rentalEndDate() != null)) {
            var start = req.rentalStartDate() != null ? req.rentalStartDate() : item.getRentalStartDate();
            var end = req.rentalEndDate() != null ? req.rentalEndDate() : item.getRentalEndDate();
            validateRentalDates(start, end);
            item.setRentalStartDate(start);
            item.setRentalEndDate(end);
        }

        cartItemRepository.save(item);
        return getCart(user);
    }

    @Transactional
    public CartSummaryDto removeCartItem(User user, Long itemId) {
        CartItem item = cartItemRepository.findByIdAndUserId(itemId, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "Không tìm thấy sản phẩm trong giỏ hàng"));
        cartItemRepository.delete(item);
        return getCart(user);
    }

    @Transactional
    public void clearCart(User user) {
        cartItemRepository.deleteByUserId(user.getId());
    }

    private void validateRentalDates(java.time.LocalDate start, java.time.LocalDate end) {
        if (start == null || end == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RENTAL_DATES_REQUIRED", "Vui lòng chọn ngày bắt đầu và kết thúc thuê");
        }
        if (!end.isAfter(start)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RENTAL_RANGE", "Ngày kết thúc thuê phải sau ngày bắt đầu");
        }
    }

    private CartItemDto toDto(CartItem item) {
        Product product = item.getProduct();
        BigDecimal unitPrice = item.getOrderType() == OrderType.RENTAL ? product.getRentPrice() : product.getBuyPrice();

        Integer rentalDays = null;
        BigDecimal subtotal;
        if (item.getOrderType() == OrderType.RENTAL) {
            rentalDays = (int) ChronoUnit.DAYS.between(item.getRentalStartDate(), item.getRentalEndDate());
            if (rentalDays < 1) rentalDays = 1;
            subtotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())).multiply(BigDecimal.valueOf(rentalDays));
        } else {
            subtotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
        }

        return new CartItemDto(
                item.getId(),
                product.getId(),
                product.getName(),
                product.getImageUrl(),
                unitPrice,
                product.getStockQuantity(),
                product.getIsAvailable(),
                item.getOrderType(),
                item.getQuantity(),
                item.getRentalStartDate(),
                item.getRentalEndDate(),
                rentalDays,
                subtotal
        );
    }
}
