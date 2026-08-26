package com.khoaluan.digishop.repository;

import com.khoaluan.digishop.entity.ProductAddon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductAddonRepository extends JpaRepository<ProductAddon, Long> {
    List<ProductAddon> findByProduct_IdOrderByDisplayOrderAscIdAsc(Long productId);
}