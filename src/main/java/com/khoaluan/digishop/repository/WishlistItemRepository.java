package com.khoaluan.digishop.repository;

import com.khoaluan.digishop.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    Optional<WishlistItem> findByUser_IdAndProduct_Id(Long userId, Long productId);

    boolean existsByUser_IdAndProduct_Id(Long userId, Long productId);

    /** Danh sách id sản phẩm mà user đã thích — dùng để tô đỏ tim ở trang danh sách/chi tiết sản phẩm. */
    @Query("select w.product.id from WishlistItem w where w.user.id = :userId")
    List<Long> findLikedProductIdsByUser(@Param("userId") Long userId);

    /** Toàn bộ mục yêu thích của 1 user, mới nhất trước — dùng cho trang "Sản phẩm yêu thích". */
    List<WishlistItem> findByUser_IdOrderByCreatedAtDesc(Long userId);

    void deleteByUser_IdAndProduct_Id(Long userId, Long productId);

    /** Xoá toàn bộ wishlist của 1 user — dùng khi admin xoá cứng tài khoản. */
    void deleteByUser_Id(Long userId);

    /** Xoá hết wishlist trỏ tới 1 sản phẩm — gọi trước khi xoá sản phẩm để tránh lỗi khoá ngoại. */
    void deleteByProduct_Id(Long productId);
}
