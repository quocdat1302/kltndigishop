package com.khoaluan.digishop.service;

import com.khoaluan.digishop.dto.ProductRequest;
import com.khoaluan.digishop.dto.RentalInventoryDto;
import com.khoaluan.digishop.dto.UpdateRentalStockRequest;
import com.khoaluan.digishop.entity.Order;
import com.khoaluan.digishop.entity.OrderItem;
import com.khoaluan.digishop.entity.OrderStatus;
import com.khoaluan.digishop.entity.OrderType;
import com.khoaluan.digishop.entity.Product;
import com.khoaluan.digishop.exception.ApiException;
import com.khoaluan.digishop.repository.OrderRepository;
import com.khoaluan.digishop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final FileStorageService fileStorageService;
    private final OrderRepository orderRepository;

    public List<Product> getAllAvailableProducts() {
        return productRepository.findAvailableOrdered();
    }

    public List<Product> getHotProducts() {
        return productRepository.findByIsHotTrue();
    }

    public List<Product> getNewProducts() {
        return productRepository.findByIsNewTrue();
    }

    public List<Product> getLatestProducts() {
        return productRepository.findLatestProducts();
    }

    public List<Product> getProductsByBrand(String brand) {
        return productRepository.findByBrand(brand);
    }

    public List<Product> getProductsByType(String type) {
        return productRepository.findByType(type);
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id).orElse(null);
    }

    /** POST /api/products/admin/{id}/images — lưu file lên đĩa rồi cập nhật imageUrl của sản phẩm. */
    public Product updateProductImage(Long id, MultipartFile file) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm"));
        String url = fileStorageService.storeProductImage(file);
        product.setImageUrl(url);
        return productRepository.save(product);
    }

    /* ==================== Admin CRUD (UC-06 / UC-07 / UC-08) ==================== */

    /** Admin xem toàn bộ sản phẩm, kể cả sản phẩm đã ngừng bán/ngừng cho thuê. */
    public List<Product> getAllProductsForAdmin() {
        return productRepository.findAllOrderedForAdmin();
    }

    public Product createProduct(ProductRequest req) {
        Integer nextOrder = null;
        if (req.displayOrder() != null) {
            nextOrder = req.displayOrder();
        } else {
            Integer max = productRepository.findMaxDisplayOrder();
            nextOrder = (max != null ? max : 0) + 1;
        }

        Product product = Product.builder()
                .name(req.name())
                .brand(req.brand())
                .type(req.type())
                .buyPrice(req.buyPrice())
                .rentPrice(req.rentPrice())
                .rentPriceWeekly(req.rentPriceWeekly())
                .rentPriceMorning(req.rentPriceMorning())
                .rentPriceAfternoon(req.rentPriceAfternoon())
                .rentPriceEvening(req.rentPriceEvening())
                .accessoriesIncluded(req.accessoriesIncluded())
                .techSpecs(req.techSpecs())
                .lensMount(req.lensMount())
                .imageUrl(req.imageUrl())
                .description(req.description())
                .stockQuantity(req.stockQuantity())
                .isAvailable(req.isAvailable() != null ? req.isAvailable() : Boolean.TRUE)
                .productCondition(req.productCondition())
                .isNew(req.isNew() != null ? req.isNew() : Boolean.FALSE)
                .isHot(req.isHot() != null ? req.isHot() : Boolean.FALSE)
                .displayOrder(nextOrder)
                .build();

        return productRepository.save(product);
    }

    public Product updateProduct(Long id, ProductRequest req) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm"));

        if (req.name() != null) product.setName(req.name());
        if (req.brand() != null) product.setBrand(req.brand());
        if (req.type() != null) product.setType(req.type());
        if (req.buyPrice() != null) product.setBuyPrice(req.buyPrice());
        if (req.rentPrice() != null) product.setRentPrice(req.rentPrice());
        if (req.rentPriceWeekly() != null) product.setRentPriceWeekly(req.rentPriceWeekly());
        if (req.rentPriceMorning() != null) product.setRentPriceMorning(req.rentPriceMorning());
        if (req.rentPriceAfternoon() != null) product.setRentPriceAfternoon(req.rentPriceAfternoon());
        if (req.rentPriceEvening() != null) product.setRentPriceEvening(req.rentPriceEvening());
        if (req.accessoriesIncluded() != null) product.setAccessoriesIncluded(req.accessoriesIncluded());
        if (req.techSpecs() != null) product.setTechSpecs(req.techSpecs());
        if (req.lensMount() != null) product.setLensMount(req.lensMount());
        if (req.imageUrl() != null) product.setImageUrl(req.imageUrl());
        if (req.description() != null) product.setDescription(req.description());
        if (req.stockQuantity() != null) product.setStockQuantity(req.stockQuantity());
        if (req.isAvailable() != null) product.setIsAvailable(req.isAvailable());
        if (req.productCondition() != null) product.setProductCondition(req.productCondition());
        if (req.isNew() != null) product.setIsNew(req.isNew());
        if (req.isHot() != null) product.setIsHot(req.isHot());
        if (req.displayOrder() != null) product.setDisplayOrder(req.displayOrder());
        product.setUpdatedAt(Instant.now());

        return productRepository.save(product);
    }

    /**
     * Cập nhật thứ tự hiển thị cho toàn bộ danh sách sản phẩm theo thứ tự id được gửi lên.
     * Dùng cho UI kéo-thả ở AdminProductsPage.
     */
    @Transactional
    public void reorderProducts(List<Long> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) return;

        List<Product> products = productRepository.findAllById(orderedIds);
        Map<Long, Product> map = new HashMap<>();
        for (Product p : products) map.put(p.getId(), p);

        // validate: đủ id
        for (Long id : orderedIds) {
            if (!map.containsKey(id)) {
                throw new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm id=" + id);
            }
        }

        for (int i = 0; i < orderedIds.size(); i++) {
            Product p = map.get(orderedIds.get(i));
            p.setDisplayOrder(i + 1);
            p.setUpdatedAt(Instant.now());
        }
        productRepository.saveAll(products);
    }

    /**
     * "Xoá" sản phẩm = ngừng bán/ngừng cho thuê (soft delete) thay vì xoá hẳn khỏi CSDL,
     * để không phá vỡ các đơn hàng / giỏ hàng cũ đang tham chiếu tới sản phẩm này.
     */
    public void deactivateProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm"));

        product.setIsAvailable(false);
        product.setUpdatedAt(Instant.now());
        productRepository.save(product);
    }

    /* ==================== Quản lý tồn kho cho thuê (UC-32) ==================== */

    /**
     * Lấy thông tin tồn kho cho thuê của tất cả sản phẩm.
     * Bao gồm: tổng stock, rental stock, số lượng đang thuê, tỷ lệ occupancy.
     */
    @Transactional(readOnly = true)
    public List<RentalInventoryDto> getRentalInventory() {
        List<Product> products = productRepository.findAll();
        return products.stream()
                .map(this::toRentalInventoryDto)
                .toList();
    }

    /**
     * Lấy thông tin tồn kho của một sản phẩm cụ thể.
     */
    @Transactional(readOnly = true)
    public RentalInventoryDto getRentalInventoryByProductId(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm"));
        return toRentalInventoryDto(product);
    }

    /**
     * Cập nhật số lượng máy cho thuê của sản phẩm.
     */
    @Transactional
    public Product updateRentalStock(Long productId, UpdateRentalStockRequest req) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm"));

        if (req.getRentalStockQuantity() != null) {
            if (req.getRentalStockQuantity() < 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY",
                        "Số lượng không thể âm");
            }
            product.setRentalStockQuantity(req.getRentalStockQuantity());
        }

        product.setUpdatedAt(Instant.now());
        return productRepository.save(product);
    }

    /**
     * Convert Product → RentalInventoryDto.
     * Tính toán: số lượng đang thuê, còn lại, tỷ lệ occupancy.
     */
    private RentalInventoryDto toRentalInventoryDto(Product product) {
        Integer rentalStock = product.getRentalStockQuantity() != null
                ? product.getRentalStockQuantity()
                : product.getStockQuantity();

        // Đếm số lượng đang thuê (trạng thái: PENDING, CONFIRMED, DELIVERING, DELIVERED)
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        Set<OrderStatus> activeStatuses = Set.of(
                OrderStatus.PENDING,
                OrderStatus.CONFIRMED,
                OrderStatus.DELIVERING,
                OrderStatus.DEPOSIT_PAID,
                OrderStatus.DELIVERED
        );

        int rented = 0;
        List<Order> allOrders = orderRepository.findByOrderTypeOrderByCreatedAtDesc(OrderType.RENTAL);
        for (Order order : allOrders) {
            if (!activeStatuses.contains(order.getStatus())) continue;
            if (order.getRentalEndDate() != null && order.getRentalEndDate().isBefore(today)) continue;

            for (OrderItem item : order.getItems()) {
                if (item.getProduct().getId().equals(product.getId())) {
                    rented += item.getQuantity();
                }
            }
        }

        Integer available = Math.max(rentalStock - rented, 0);
        Integer occupancyPercent = rentalStock > 0 ? (rented * 100) / rentalStock : 0;

        return RentalInventoryDto.builder()
                .id(product.getId())
                .name(product.getName())
                .brand(product.getBrand())
                .type(product.getType())
                .rentPrice(product.getRentPrice())
                .totalStock(product.getStockQuantity())
                .rentalStock(rentalStock)
                .rented(rented)
                .available(available)
                .occupancyPercent(occupancyPercent)
                .isActive(product.getIsAvailable())
                .build();
    }
}
