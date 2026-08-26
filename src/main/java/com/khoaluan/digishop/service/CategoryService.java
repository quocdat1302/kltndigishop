package com.khoaluan.digishop.service;

import com.khoaluan.digishop.dto.CategoryRequest;
import com.khoaluan.digishop.entity.Category;
import com.khoaluan.digishop.exception.ApiException;
import com.khoaluan.digishop.repository.CategoryRepository;
import com.khoaluan.digishop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    /**
     * productCount lưu trong bảng categories chỉ là số liệu seed cũ, không tự cập nhật khi sản phẩm được
     * thêm/xoá/ẩn — nên ở đây tính lại SỐ THẬT theo sản phẩm đang bán (isAvailable=true) trước khi trả về,
     * để menu "Bộ sưu tập" ngoài trang chủ luôn khớp với số sản phẩm thực tế.
     */
    public List<Category> getBrands() {
        List<Category> brands = categoryRepository.findByTypeOrderByProductCountDesc("brand");
        brands.forEach(b -> b.setProductCount((int) productRepository.countByBrandIgnoreCaseAndIsAvailableTrue(b.getName())));
        brands.sort((a, b) -> b.getProductCount() - a.getProductCount());
        return brands;
    }

    public List<Category> getProductTypes() {
        List<Category> types = categoryRepository.findByTypeOrderByProductCountDesc("category");
        types.forEach(t -> t.setProductCount((int) productRepository.countByTypeIgnoreCaseAndIsAvailableTrue(t.getName())));
        types.sort((a, b) -> b.getProductCount() - a.getProductCount());
        return types;
    }

    /* ==================== Admin CRUD (UC-06) ==================== */

    public Category createCategory(CategoryRequest req) {
        if (categoryRepository.findAll().stream().anyMatch(c -> c.getName().equalsIgnoreCase(req.name()))) {
            throw new ApiException(HttpStatus.CONFLICT, "CATEGORY_NAME_TAKEN", "Tên danh mục đã tồn tại",
                    Map.of("name", req.name()));
        }

        Category category = Category.builder()
                .name(req.name())
                .type(req.type())
                .imageUrl(req.imageUrl())
                .productCount(0)
                .build();

        return categoryRepository.save(category);
    }

    public Category updateCategory(Long id, CategoryRequest req) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "Không tìm thấy danh mục"));

        if (req.name() != null) category.setName(req.name());
        if (req.type() != null) category.setType(req.type());
        if (req.imageUrl() != null) category.setImageUrl(req.imageUrl());

        return categoryRepository.save(category);
    }

    public void deleteCategory(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "Không tìm thấy danh mục");
        }
        categoryRepository.deleteById(id);
    }
}