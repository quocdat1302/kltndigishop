package com.khoaluan.digishop.repository;

import com.khoaluan.digishop.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Sắp xếp theo displayOrder (thứ tự admin kéo thả) trước, fallback theo createdAt để ổn định.
     * COALESCE để tránh null nằm lên đầu danh sách.
     */
    @Query("SELECT p FROM Product p WHERE p.isAvailable = true ORDER BY COALESCE(p.displayOrder, 999999) ASC, p.createdAt DESC")
    List<Product> findAvailableOrdered();

    List<Product> findByIsHotTrue();

    List<Product> findByIsNewTrue();

    @Query("SELECT p FROM Product p WHERE p.isAvailable = true ORDER BY p.createdAt DESC")
    List<Product> findLatestProducts();

    /** Admin xem toàn bộ (kể cả ngừng bán), theo displayOrder. */
    @Query("SELECT p FROM Product p ORDER BY COALESCE(p.displayOrder, 999999) ASC, p.createdAt DESC")
    List<Product> findAllOrderedForAdmin();

    @Query("SELECT COALESCE(MAX(p.displayOrder), 0) FROM Product p")
    Integer findMaxDisplayOrder();

    List<Product> findByBrand(String brand);

    List<Product> findByType(String type);

    long countByBrandIgnoreCaseAndIsAvailableTrue(String brand);

    long countByTypeIgnoreCaseAndIsAvailableTrue(String type);
}
