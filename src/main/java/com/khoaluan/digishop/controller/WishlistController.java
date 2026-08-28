package com.khoaluan.digishop.controller;

import com.khoaluan.digishop.dto.WishlistProductDto;
import com.khoaluan.digishop.entity.User;
import com.khoaluan.digishop.service.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Yêu cầu đăng nhập cho toàn bộ endpoint ở đây (khớp SecurityConfig: "/api/wishlist/**"
 * không nằm trong danh sách permitAll nên rơi vào .anyRequest().authenticated()).
 */
@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    /** Danh sách id sản phẩm user đang thích — frontend gọi 1 lần khi vào trang danh sách/chi tiết sản phẩm để tô tim đỏ. */
    @GetMapping("/product-ids")
    public List<Long> getLikedProductIds(@AuthenticationPrincipal User user) {
        return wishlistService.getLikedProductIds(user);
    }

    /** Danh sách đầy đủ sản phẩm đã thích — dùng cho trang "Sản phẩm yêu thích". */
    @GetMapping
    public List<WishlistProductDto> getWishlist(@AuthenticationPrincipal User user) {
        return wishlistService.getWishlistProducts(user);
    }

    @PostMapping("/{productId}")
    public ResponseEntity<Void> addToWishlist(@AuthenticationPrincipal User user, @PathVariable Long productId) {
        wishlistService.addToWishlist(user, productId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> removeFromWishlist(@AuthenticationPrincipal User user, @PathVariable Long productId) {
        wishlistService.removeFromWishlist(user, productId);
        return ResponseEntity.noContent().build();
    }

    /** Bật/tắt yêu thích trong 1 lần gọi — frontend dùng cho nút tim (không cần tự biết trạng thái hiện tại trước). */
    @PostMapping("/{productId}/toggle")
    public Map<String, Boolean> toggleWishlist(@AuthenticationPrincipal User user, @PathVariable Long productId) {
        boolean liked = wishlistService.toggleWishlist(user, productId);
        return Map.of("liked", liked);
    }
}
