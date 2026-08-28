package com.khoaluan.digishop.service;

import com.khoaluan.digishop.dto.WishlistProductDto;
import com.khoaluan.digishop.entity.Product;
import com.khoaluan.digishop.entity.User;
import com.khoaluan.digishop.entity.WishlistItem;
import com.khoaluan.digishop.exception.ApiException;
import com.khoaluan.digishop.repository.ProductRepository;
import com.khoaluan.digishop.repository.WishlistItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WishlistService {

    private final WishlistItemRepository wishlistItemRepository;
    private final ProductRepository productRepository;

    /** Danh sách id sản phẩm user đang thích — dùng để tô đỏ tim ở trang danh sách/chi tiết sản phẩm. */
    @Transactional(readOnly = true)
    public List<Long> getLikedProductIds(User user) {
        return wishlistItemRepository.findLikedProductIdsByUser(user.getId());
    }

    /** Danh sách đầy đủ thông tin sản phẩm đã thích — dùng cho trang "Sản phẩm yêu thích". */
    @Transactional(readOnly = true)
    public List<WishlistProductDto> getWishlistProducts(User user) {
        return wishlistItemRepository.findByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(WishlistItem::getProduct)
                .map(this::toDto)
                .toList();
    }

    /** Thêm 1 sản phẩm vào wishlist. Idempotent — nếu đã thích rồi thì không lỗi, không tạo trùng. */
    @Transactional
    public void addToWishlist(User user, Long productId) {
        if (wishlistItemRepository.existsByUser_IdAndProduct_Id(user.getId(), productId)) {
            return;
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm"));

        WishlistItem item = WishlistItem.builder()
                .user(user)
                .product(product)
                .build();
        wishlistItemRepository.save(item);
    }

    /** Bỏ thích 1 sản phẩm. Idempotent — nếu chưa thích thì không lỗi. */
    @Transactional
    public void removeFromWishlist(User user, Long productId) {
        wishlistItemRepository.deleteByUser_IdAndProduct_Id(user.getId(), productId);
    }

    /** Bật/tắt trạng thái thích, trả về true nếu SAU khi gọi xong sản phẩm đang ở trạng thái "đã thích". */
    @Transactional
    public boolean toggleWishlist(User user, Long productId) {
        boolean alreadyLiked = wishlistItemRepository.existsByUser_IdAndProduct_Id(user.getId(), productId);
        if (alreadyLiked) {
            wishlistItemRepository.deleteByUser_IdAndProduct_Id(user.getId(), productId);
            return false;
        } else {
            addToWishlist(user, productId);
            return true;
        }
    }

    private WishlistProductDto toDto(Product p) {
        return new WishlistProductDto(
                p.getId(), p.getName(), p.getBrand(), p.getType(),
                p.getBuyPrice(), p.getRentPrice(), p.getImageUrl(), p.getIsAvailable()
        );
    }
}
