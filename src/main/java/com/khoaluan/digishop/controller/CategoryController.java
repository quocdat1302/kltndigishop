package com.khoaluan.digishop.controller;

import com.khoaluan.digishop.dto.CategoryDto;
import com.khoaluan.digishop.dto.CategoryRequest;
import com.khoaluan.digishop.entity.Category;
import com.khoaluan.digishop.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /* ==================== Public ==================== */

    @GetMapping
    public List<CategoryDto> getAllCategories() {
        return categoryService.getAllCategories().stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/brands")
    public List<CategoryDto> getBrands() {
        return categoryService.getBrands().stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/types")
    public List<CategoryDto> getProductTypes() {
        return categoryService.getProductTypes().stream()
                .map(this::toDto)
                .toList();
    }

    /* ==================== Admin (UC-06) ==================== */

    @PostMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryDto createCategory(@Valid @RequestBody CategoryRequest req) {
        return toDto(categoryService.createCategory(req));
    }

    @PutMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryDto updateCategory(@PathVariable Long id, @RequestBody CategoryRequest req) {
        return toDto(categoryService.updateCategory(id, req));
    }

    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
    }

    private CategoryDto toDto(Category category) {
        return new CategoryDto(
                category.getId(),
                category.getName(),
                category.getType(),
                category.getProductCount(),
                category.getImageUrl()
        );
    }
}