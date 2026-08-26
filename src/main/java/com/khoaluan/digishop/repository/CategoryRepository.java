package com.khoaluan.digishop.repository;

import com.khoaluan.digishop.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByType(String type);

    List<Category> findByTypeOrderByProductCountDesc(String type);
}