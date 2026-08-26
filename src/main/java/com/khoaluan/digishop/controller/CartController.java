package com.khoaluan.digishop.controller;

import com.khoaluan.digishop.dto.AddToCartRequest;
import com.khoaluan.digishop.dto.CartSummaryDto;
import com.khoaluan.digishop.dto.UpdateCartItemRequest;
import com.khoaluan.digishop.entity.User;
import com.khoaluan.digishop.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public CartSummaryDto getCart(@AuthenticationPrincipal User user) {
        return cartService.getCart(user);
    }

    @PostMapping("/items")
    public ResponseEntity<CartSummaryDto> addToCart(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AddToCartRequest req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cartService.addToCart(user, req));
    }

    @PutMapping("/items/{itemId}")
    public CartSummaryDto updateCartItem(
            @AuthenticationPrincipal User user,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateCartItemRequest req
    ) {
        return cartService.updateCartItem(user, itemId, req);
    }

    @DeleteMapping("/items/{itemId}")
    public CartSummaryDto removeCartItem(
            @AuthenticationPrincipal User user,
            @PathVariable Long itemId
    ) {
        return cartService.removeCartItem(user, itemId);
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(@AuthenticationPrincipal User user) {
        cartService.clearCart(user);
        return ResponseEntity.noContent().build();
    }
}
