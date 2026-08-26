package com.khoaluan.digishop.repository;

import com.khoaluan.digishop.entity.ProductSamplePhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductSamplePhotoRepository extends JpaRepository<ProductSamplePhoto, Long> {
    List<ProductSamplePhoto> findByProduct_IdOrderByIdDesc(Long productId);
}