package com.khoaluan.digishop.repository;

import com.khoaluan.digishop.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByIsAvailableTrue();

    List<Product> findByIsHotTrue();

    List<Product> findByIsNewTrue();

    @Query("SELECT p FROM Product p WHERE p.isAvailable = true ORDER BY p.createdAt DESC")
    List<Product> findLatestProducts();

    List<Product> findByBrand(String brand);

    List<Product> findByType(String type);

    long countByBrandIgnoreCaseAndIsAvailableTrue(String brand);

    long countByTypeIgnoreCaseAndIsAvailableTrue(String type);
}