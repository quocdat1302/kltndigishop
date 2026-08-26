package com.khoaluan.digishop.controller;

import com.khoaluan.digishop.dto.CreateProductAddonRequest;
import com.khoaluan.digishop.dto.CreateSamplePhotoRequest;
import com.khoaluan.digishop.dto.ProductAddonDto;
import com.khoaluan.digishop.dto.ProductDto;
import com.khoaluan.digishop.dto.ProductRequest;
import com.khoaluan.digishop.dto.ProductSamplePhotoDto;
import com.khoaluan.digishop.dto.RentalInventoryDto;
import com.khoaluan.digishop.dto.UpdateRentalStockRequest;
import com.khoaluan.digishop.entity.Product;
import com.khoaluan.digishop.entity.ProductAddon;
import com.khoaluan.digishop.entity.ProductSamplePhoto;
import com.khoaluan.digishop.exception.ApiException;
import com.khoaluan.digishop.repository.ProductAddonRepository;
import com.khoaluan.digishop.repository.ProductRepository;
import com.khoaluan.digishop.repository.ProductReviewRepository;
import com.khoaluan.digishop.repository.ProductSamplePhotoRepository;
import com.khoaluan.digishop.service.ProductReviewService;
import com.khoaluan.digishop.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductReviewService productReviewService;
    private final ProductSamplePhotoRepository productSamplePhotoRepository;
    private final ProductAddonRepository productAddonRepository;
    private final ProductRepository productRepository;

    /* ==================== Public ==================== */

    @GetMapping
    public List<ProductDto> getAllProducts() {
        return toDtoList(productService.getAllAvailableProducts());
    }

    @GetMapping("/hot")
    public List<ProductDto> getHotProducts() {
        return toDtoList(productService.getHotProducts());
    }

    @GetMapping("/new")
    public List<ProductDto> getNewProducts() {
        return toDtoList(productService.getNewProducts());
    }

    @GetMapping("/latest")
    public List<ProductDto> getLatestProducts() {
        return toDtoList(productService.getLatestProducts());
    }

    @GetMapping("/brand/{brand}")
    public List<ProductDto> getProductsByBrand(@PathVariable String brand) {
        return toDtoList(productService.getProductsByBrand(brand));
    }

    @GetMapping("/type/{type}")
    public List<ProductDto> getProductsByType(@PathVariable String type) {
        return toDtoList(productService.getProductsByType(type));
    }

    @GetMapping("/{id}")
    public ProductDto getProductById(@PathVariable Long id) {
        Product product = productService.getProductById(id);
        return product != null ? toDto(product) : null;
    }

    /* ==================== Admin (UC-06 / UC-07 / UC-08) ==================== */

    @GetMapping("/admin/all")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public List<ProductDto> getAllProductsForAdmin() {
        return toDtoList(productService.getAllProductsForAdmin());
    }

    @PostMapping("/admin")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDto createProduct(@Valid @RequestBody ProductRequest req) {
        return toDto(productService.createProduct(req));
    }

    @PutMapping("/admin/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ProductDto updateProduct(@PathVariable Long id, @RequestBody ProductRequest req) {
        return toDto(productService.updateProduct(id, req));
    }

    /** Soft-delete: đánh dấu ngừng bán/ngừng cho thuê, không xoá khỏi CSDL. */
    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable Long id) {
        productService.deactivateProduct(id);
    }

    /** Upload ảnh sản phẩm - lưu đĩa cục bộ, trả về ProductDto với imageUrl mới. */
    @PostMapping(value = "/admin/{id}/images", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ProductDto uploadProductImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return toDto(productService.updateProductImage(id, file));
    }

    /* ==================== Quản lý tồn kho cho thuê (UC-32) ==================== */

    /** Lấy danh sách tồn kho cho thuê của tất cả sản phẩm. */
    @GetMapping("/admin/rental-inventory")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public List<RentalInventoryDto> getRentalInventory() {
        return productService.getRentalInventory();
    }

    /** Lấy thông tin tồn kho cho thuê của một sản phẩm cụ thể. */
    @GetMapping("/admin/{id}/rental-inventory")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public RentalInventoryDto getRentalInventoryByProductId(@PathVariable Long id) {
        return productService.getRentalInventoryByProductId(id);
    }

    /** Cập nhật số lượng máy cho thuê của sản phẩm. */
    @PutMapping("/admin/{id}/rental-stock")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public RentalInventoryDto updateRentalStock(@PathVariable Long id, @Valid @RequestBody UpdateRentalStockRequest req) {
        productService.updateRentalStock(id, req);
        return productService.getRentalInventoryByProductId(id);
    }

    /* ==================== Ảnh mẫu (feedback khách chụp bằng máy này) ==================== */

    @GetMapping("/{id}/sample-photos")
    public List<ProductSamplePhotoDto> getSamplePhotos(@PathVariable Long id) {
        return productSamplePhotoRepository.findByProduct_IdOrderByIdDesc(id).stream()
                .map(this::toSamplePhotoDto)
                .toList();
    }

    @PostMapping("/admin/{id}/sample-photos")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductSamplePhotoDto addSamplePhoto(@PathVariable Long id, @Valid @RequestBody CreateSamplePhotoRequest req) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm"));
        ProductSamplePhoto saved = productSamplePhotoRepository.save(ProductSamplePhoto.builder()
                .product(product)
                .imageUrl(req.imageUrl())
                .caption(req.caption())
                .build());
        return toSamplePhotoDto(saved);
    }

    @DeleteMapping("/admin/sample-photos/{photoId}")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSamplePhoto(@PathVariable Long photoId) {
        productSamplePhotoRepository.deleteById(photoId);
    }

    private ProductSamplePhotoDto toSamplePhotoDto(ProductSamplePhoto p) {
        return new ProductSamplePhotoDto(p.getId(), p.getProduct().getId(), p.getImageUrl(), p.getCaption());
    }

    /* ==================== Phụ kiện bổ sung khi thuê (đi kèm miễn phí hoặc trả thêm) ==================== */

    @GetMapping("/{id}/addons")
    public List<ProductAddonDto> getAddons(@PathVariable Long id) {
        return productAddonRepository.findByProduct_IdOrderByDisplayOrderAscIdAsc(id).stream()
                .map(this::toAddonDto)
                .toList();
    }

    @PostMapping("/admin/{id}/addons")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductAddonDto addAddon(@PathVariable Long id, @Valid @RequestBody CreateProductAddonRequest req) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm"));
        ProductAddon saved = productAddonRepository.save(ProductAddon.builder()
                .product(product)
                .name(req.name())
                .price(req.included() ? java.math.BigDecimal.ZERO : (req.price() != null ? req.price() : java.math.BigDecimal.ZERO))
                .included(req.included())
                .displayOrder(req.displayOrder() != null ? req.displayOrder() : 0)
                .build());
        return toAddonDto(saved);
    }

    @DeleteMapping("/admin/addons/{addonId}")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAddon(@PathVariable Long addonId) {
        productAddonRepository.deleteById(addonId);
    }

    private ProductAddonDto toAddonDto(ProductAddon a) {
        return new ProductAddonDto(a.getId(), a.getProduct().getId(), a.getName(), a.getPrice(), a.isIncluded());
    }

    /* ==================== Mapping ==================== */

    private List<ProductDto> toDtoList(List<Product> products) {
        Map<Long, ProductReviewRepository.RatingSummary> ratingMap = productReviewService.getAllRatingSummaries();
        return products.stream().map(p -> toDto(p, ratingMap)).toList();
    }

    private ProductDto toDto(Product product) {
        return toDto(product, productReviewService.getAllRatingSummaries());
    }

    private ProductDto toDto(Product product, Map<Long, ProductReviewRepository.RatingSummary> ratingMap) {
        ProductReviewRepository.RatingSummary summary = ratingMap.get(product.getId());
        List<ProductSamplePhotoDto> samplePhotos = productSamplePhotoRepository
                .findByProduct_IdOrderByIdDesc(product.getId()).stream()
                .map(this::toSamplePhotoDto)
                .toList();
        List<ProductAddonDto> addons = productAddonRepository
                .findByProduct_IdOrderByDisplayOrderAscIdAsc(product.getId()).stream()
                .map(this::toAddonDto)
                .toList();
        return new ProductDto(
                product.getId(),
                product.getName(),
                product.getBrand(),
                product.getType(),
                product.getBuyPrice(),
                product.getRentPrice(),
                product.getRentPriceWeekly(),
                product.getRentPriceMorning(),
                product.getRentPriceAfternoon(),
                product.getRentPriceEvening(),
                product.getAccessoriesIncluded(),
                product.getTechSpecs(),
                product.getLensMount(),
                product.getImageUrl(),
                product.getDescription(),
                product.getStockQuantity(),
                product.getIsAvailable(),
                product.getProductCondition(),
                product.getIsNew(),
                product.getIsHot(),
                summary != null ? summary.getAvgRating() : null,
                summary != null ? summary.getReviewCount() : 0L,
                samplePhotos,
                addons
        );
    }
}