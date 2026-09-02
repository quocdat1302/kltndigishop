package com.khoaluan.digishop.service;

import com.khoaluan.digishop.dto.CheckoutPurchaseRequest;
import com.khoaluan.digishop.dto.CheckoutRentalRequest;
import com.khoaluan.digishop.dto.DayAvailabilityDto;
import com.khoaluan.digishop.dto.OrderAddonDto;
import com.khoaluan.digishop.dto.OrderDto;
import com.khoaluan.digishop.dto.OrderItemDto;
import com.khoaluan.digishop.dto.ProductStockBreakdownDto;
import com.khoaluan.digishop.dto.RentalCalendarEntryDto;
import com.khoaluan.digishop.dto.RentalInventoryEntryDto;
import com.khoaluan.digishop.dto.RentalContractDto;
import com.khoaluan.digishop.entity.*;
import com.khoaluan.digishop.exception.ApiException;
import com.khoaluan.digishop.repository.CartItemRepository;
import com.khoaluan.digishop.repository.OrderItemRepository;
import com.khoaluan.digishop.repository.OrderRepository;
import com.khoaluan.digishop.repository.ProductAddonRepository;
import com.khoaluan.digishop.repository.ProductRepository;
import com.khoaluan.digishop.repository.PickupLocationRepository;
import com.khoaluan.digishop.repository.RentalContractRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    /** Tỉ lệ tiền cọc áp dụng cho đơn thuê, tính trên tổng tiền thuê. */
    private static final BigDecimal RENTAL_DEPOSIT_RATE = new BigDecimal("0.30");

    /** Số ngày khách được phép yêu cầu đổi trả sau khi đơn mua hoàn tất. */
    private static final int RETURN_WINDOW_DAYS = 7;

    /** Các trạng thái thuê được coi là "đang chiếm dụng" sản phẩm, dùng để kiểm tra trùng lịch. */
    private static final Set<OrderStatus> ACTIVE_RENTAL_STATUSES = EnumSet.of(
            OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.DEPOSIT_PAID, OrderStatus.DELIVERED, OrderStatus.RENTAL_RETURN_REQUESTED);

    /** Các bước riêng của quy trình thuê — không được set trực tiếp qua API updateOrderStatus chung. */
    private static final Set<OrderStatus> RENTAL_FLOW_ONLY_STATUSES = EnumSet.of(
            OrderStatus.DEPOSIT_PAID, OrderStatus.DELIVERED, OrderStatus.RENTAL_RETURN_REQUESTED, OrderStatus.RENTAL_RETURNED,
            OrderStatus.INSPECTED, OrderStatus.DISPUTED);

    /** Nội dung thông báo trong-app tương ứng mỗi trạng thái đơn - dùng ở updateOrderStatus (API chung). */
    private static final Map<OrderStatus, String> STATUS_NOTIFICATION_TITLE = Map.of(
            OrderStatus.CONFIRMED, "Đơn hàng đã được xác nhận",
            OrderStatus.DELIVERING, "Đơn hàng đang được giao",
            OrderStatus.COMPLETED, "Đơn hàng đã hoàn tất",
            OrderStatus.CANCELLED, "Đơn hàng đã bị huỷ"
    );
    private static final Map<OrderStatus, String> STATUS_NOTIFICATION_BODY = Map.of(
            OrderStatus.CONFIRMED, "đã được xác nhận và đang được chuẩn bị.",
            OrderStatus.DELIVERING, "đang trên đường giao tới bạn.",
            OrderStatus.COMPLETED, "đã hoàn tất. Cảm ơn bạn đã mua sắm tại DigiShop!",
            OrderStatus.CANCELLED, "đã bị huỷ."
    );

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductAddonRepository productAddonRepository;
    private final PickupLocationRepository pickupLocationRepository;
    private final RentalContractRepository rentalContractRepository;
    private final PromotionService promotionService;
    private final LoyaltyService loyaltyService;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final SecureRandom random = new SecureRandom();

    // ------------------------------------------------------------------
    // Đặt mua (Purchase)
    // ------------------------------------------------------------------

    @Transactional
    public OrderDto checkoutPurchase(User user, CheckoutPurchaseRequest req) {
        List<CartItem> sourceCartItems = new ArrayList<>();
        List<OrderItem> orderItems = new ArrayList<>();

        if (req.buyNowProductId() != null) {
            int qty = req.buyNowQuantity() != null ? req.buyNowQuantity() : 1;
            if (qty < 1) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY", "Số lượng phải lớn hơn 0");
            }
            Product product = productRepository.findById(req.buyNowProductId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Sản phẩm không tồn tại"));
            orderItems.add(buildPurchaseOrderItem(product, qty));
        } else {
            List<CartItem> cartItems = cartItemRepository
                    .findByUserIdAndOrderTypeOrderByCreatedAtDesc(user.getId(), OrderType.PURCHASE);

            if (req.cartItemIds() != null && !req.cartItemIds().isEmpty()) {
                Set<Long> wanted = Set.copyOf(req.cartItemIds());
                cartItems = cartItems.stream().filter(ci -> wanted.contains(ci.getId())).toList();
            }

            if (cartItems.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_CART", "Không có sản phẩm nào để đặt mua");
            }

            for (CartItem ci : cartItems) {
                orderItems.add(buildPurchaseOrderItem(ci.getProduct(), ci.getQuantity()));
            }
            sourceCartItems = cartItems;
        }

        BigDecimal subtotal = orderItems.stream().map(OrderItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        String appliedCode = null;
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (req.promotionCode() != null && !req.promotionCode().isBlank()) {
            Promotion promotion = promotionService.validateCode(req.promotionCode());
            appliedCode = promotion.getCode();
            discountAmount = subtotal.multiply(promotion.getDiscountPercent())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }

        // Khách hàng thân thiết được tự động giảm giá thêm — cộng dồn với mã khuyến mãi nếu có, không cần thao tác gì.
        BigDecimal loyaltyDiscountPercent = loyaltyService.getAutoDiscountPercent(user.getId());
        BigDecimal loyaltyDiscountAmount = LoyaltyService.calcDiscountAmount(subtotal, loyaltyDiscountPercent);

        BigDecimal total = subtotal.subtract(discountAmount).subtract(loyaltyDiscountAmount);

        FulfillmentMethod fulfillmentMethod = parseFulfillmentMethod(req.fulfillmentMethod());
        String pickupLocationName = null;
        BigDecimal pickupFee = BigDecimal.ZERO;
        boolean requiresAddress;

        if (req.pickupLocationId() != null) {
            PickupLocation location = pickupLocationRepository.findById(req.pickupLocationId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LOCATION_NOT_FOUND", "Không tìm thấy địa điểm nhận hàng"));
            pickupLocationName = location.getName();
            pickupFee = location.getFee() != null ? location.getFee() : BigDecimal.ZERO;
            fulfillmentMethod = location.isDelivery() ? FulfillmentMethod.HOME_DELIVERY : FulfillmentMethod.PICKUP_AT_SHOP;
            requiresAddress = location.isDelivery();
        } else {
            requiresAddress = fulfillmentMethod == FulfillmentMethod.HOME_DELIVERY;
        }

        if (requiresAddress && (req.shippingAddress() == null || req.shippingAddress().isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SHIPPING_ADDRESS_REQUIRED",
                    "Vui lòng nhập địa chỉ nhận hàng, hoặc chọn địa điểm không cần giao tận nơi");
        }

        total = total.add(pickupFee);

        Order order = Order.builder()
                .orderCode(generateOrderCode("DM"))
                .user(user)
                .orderType(OrderType.PURCHASE)
                .status(OrderStatus.PENDING)
                .recipientName(req.recipientName())
                .recipientPhone(req.recipientPhone())
                .shippingAddress(requiresAddress ? req.shippingAddress() : null)
                .fulfillmentMethod(fulfillmentMethod)
                .pickupLocationName(pickupLocationName)
                .pickupFee(pickupFee)
                .note(req.note())
                .subtotalAmount(subtotal)
                .promotionCode(appliedCode)
                .discountAmount(discountAmount)
                .loyaltyDiscountAmount(loyaltyDiscountAmount)
                .depositAmount(BigDecimal.ZERO)
                .totalAmount(total)
                .build();

        attachItems(order, orderItems);
        Order saved = orderRepository.save(order);

        if (!sourceCartItems.isEmpty()) {
            cartItemRepository.deleteAll(sourceCartItems);
        }

        emailService.sendOrderConfirmationEmail(user.getEmail(), new OrderEmailData(
                saved.getOrderCode(), false, saved.getRecipientName(), saved.getRecipientPhone(),
                fulfillmentMethod == FulfillmentMethod.PICKUP_AT_SHOP ? "Nhận tại shop" : saved.getShippingAddress(),
                null, null, null, BigDecimal.ZERO, saved.getTotalAmount(),
                orderItems.stream()
                        .map(i -> new OrderEmailData.Line(i.getProductName(), i.getQuantity(), i.getSubtotal()))
                        .toList()
        ));
        notificationService.create(user.getId(), "Đặt hàng thành công",
                "Đơn " + saved.getOrderCode() + " đã được ghi nhận, đang chờ xác nhận.",
                NotificationType.ORDER_UPDATE, saved.getId());
        notificationService.notifyAllAdmins("🛒 Đơn mua hàng mới",
                "Đơn " + saved.getOrderCode() + " của " + saved.getRecipientName() + " vừa được đặt, đang chờ xác nhận.",
                NotificationType.ORDER_UPDATE, saved.getId());

        return toDto(saved);
    }

    private OrderItem buildPurchaseOrderItem(Product product, int quantity) {
        if (Boolean.FALSE.equals(product.getIsAvailable())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRODUCT_UNAVAILABLE",
                    "Sản phẩm \"" + product.getName() + "\" hiện không khả dụng");
        }
        if (quantity < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY", "Số lượng phải lớn hơn 0");
        }
        // Không chỉ so tồn kho vật lý — vì máy đang được giữ chỗ bởi các đơn thuê trong tương lai
        // (dù chưa trừ kho, kho chỉ trừ lúc giao máy DELIVERED) vẫn phải được chừa lại để giao đúng hẹn,
        // nếu không sẽ xảy ra tình trạng bán mất máy đã hứa cho người thuê.
        int reservedForFutureRentals = maxConcurrentFutureRentalQuantity(product.getId());
        int availableToSell = product.getStockQuantity() - reservedForFutureRentals;
        if (quantity > availableToSell) {
            String detail = reservedForFutureRentals > 0
                    ? " (đang có " + reservedForFutureRentals + " máy bị giữ chỗ bởi đơn thuê sắp tới)"
                    : "";
            throw new ApiException(HttpStatus.BAD_REQUEST, "OUT_OF_STOCK",
                    "Sản phẩm \"" + product.getName() + "\" chỉ còn " + Math.max(availableToSell, 0)
                            + " máy có thể bán" + detail);
        }

        // Trừ kho ngay khi đặt hàng để tránh bán vượt tồn kho.
        product.setStockQuantity(product.getStockQuantity() - quantity);
        if (product.getStockQuantity() <= 0) {
            product.setIsAvailable(false);
        }
        productRepository.save(product);

        BigDecimal subtotal = product.getBuyPrice().multiply(BigDecimal.valueOf(quantity));

        return OrderItem.builder()
                .product(product)
                .productName(product.getName())
                .productImageUrl(product.getImageUrl())
                .unitPrice(product.getBuyPrice())
                .quantity(quantity)
                .subtotal(subtotal)
                .build();
    }

    // ------------------------------------------------------------------
    // Đặt thuê (Rental)
    // ------------------------------------------------------------------

    @Transactional
    public OrderDto checkoutRental(User user, CheckoutRentalRequest req) {
        if (!user.isIdentityVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ID_VERIFICATION_REQUIRED",
                    "Bạn cần xác thực CCCD/CMND trước khi thuê thiết bị (xem POST /api/users/me/verify-id)");
        }

        List<CartItem> sourceCartItems = new ArrayList<>();
        List<OrderItem> orderItems = new ArrayList<>();
        List<OrderAddon> addonEntities = new ArrayList<>();
        BigDecimal addonTotal = BigDecimal.ZERO;
        LocalDate overallStart = null;
        LocalDate overallEnd = null;

        if (req.rentNowProductId() != null) {
            int qty = req.rentNowQuantity() != null ? req.rentNowQuantity() : 1;
            boolean isSlotBooking = req.rentNowSlot() != null && !req.rentNowSlot().isBlank();
            validateRentalDates(req.rentNowStartDate(), req.rentNowEndDate(), isSlotBooking);
            Product product = productRepository.findById(req.rentNowProductId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Sản phẩm không tồn tại"));
            orderItems.add(buildRentalOrderItem(product, qty, req.rentNowStartDate(), req.rentNowEndDate(), req.rentNowSlot()));
            overallStart = req.rentNowStartDate();
            overallEnd = req.rentNowEndDate();

            // Phụ kiện: các món included=true luôn tự động đi kèm miễn phí; các món trả thêm chỉ cộng
            // tiền nếu khách có chọn (id nằm trong selectedAddonIds) — và phải thuộc đúng sản phẩm này.
            Set<Long> selectedIds = req.selectedAddonIds() != null ? Set.copyOf(req.selectedAddonIds()) : Set.of();
            List<ProductAddon> productAddons = productAddonRepository.findByProduct_IdOrderByDisplayOrderAscIdAsc(product.getId());
            for (ProductAddon addon : productAddons) {
                if (addon.isIncluded()) {
                    addonEntities.add(OrderAddon.builder().name(addon.getName()).price(BigDecimal.ZERO).included(true).build());
                } else if (selectedIds.contains(addon.getId())) {
                    addonEntities.add(OrderAddon.builder().name(addon.getName()).price(addon.getPrice()).included(false).build());
                    addonTotal = addonTotal.add(addon.getPrice());
                }
            }
        } else {
            List<CartItem> cartItems = cartItemRepository
                    .findByUserIdAndOrderTypeOrderByCreatedAtDesc(user.getId(), OrderType.RENTAL);

            if (req.cartItemIds() != null && !req.cartItemIds().isEmpty()) {
                Set<Long> wanted = Set.copyOf(req.cartItemIds());
                cartItems = cartItems.stream().filter(ci -> wanted.contains(ci.getId())).toList();
            }

            if (cartItems.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_CART", "Không có sản phẩm nào để đặt thuê");
            }

            for (CartItem ci : cartItems) {
                validateRentalDates(ci.getRentalStartDate(), ci.getRentalEndDate(), false);
                orderItems.add(buildRentalOrderItem(ci.getProduct(), ci.getQuantity(), ci.getRentalStartDate(), ci.getRentalEndDate(), null));
                if (overallStart == null || ci.getRentalStartDate().isBefore(overallStart)) overallStart = ci.getRentalStartDate();
                if (overallEnd == null || ci.getRentalEndDate().isAfter(overallEnd)) overallEnd = ci.getRentalEndDate();
            }
            sourceCartItems = cartItems;
        }

        BigDecimal subtotal = orderItems.stream().map(OrderItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        String appliedCode = null;
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (req.promotionCode() != null && !req.promotionCode().isBlank()) {
            Promotion promotion = promotionService.validateCode(req.promotionCode());
            appliedCode = promotion.getCode();
            discountAmount = subtotal.multiply(promotion.getDiscountPercent())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }

        // Tien coc PHAI tinh tren gia tri THIET BI (Product.buyPrice x quantity), khong phai gia
        // thue: gia thue chi la phi su dung ngan han, con coc phai du suc rang buoc khach tra may -
        // neu tinh % tren gia thue (vd 270k/ngay) thi coc chi vai chuc nghin, trong khi may co the
        // tri gia hang chuc trieu, khong co tac dung bao dam gi ca.
        BigDecimal deviceValue = orderItems.stream()
                .map(item -> {
                    BigDecimal buyPrice = item.getProduct() != null && item.getProduct().getBuyPrice() != null
                            ? item.getProduct().getBuyPrice()
                            : BigDecimal.ZERO;
                    return buyPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deposit = deviceValue.multiply(RENTAL_DEPOSIT_RATE);

        // Khách hàng thân thiết được tự động giảm giá thêm trên tiền thuê — cộng dồn với mã khuyến mãi nếu có.
        BigDecimal loyaltyDiscountPercent = loyaltyService.getAutoDiscountPercent(user.getId());
        BigDecimal loyaltyDiscountAmount = LoyaltyService.calcDiscountAmount(subtotal, loyaltyDiscountPercent);

        BigDecimal total = subtotal.subtract(discountAmount).subtract(loyaltyDiscountAmount).add(deposit).add(addonTotal);
        int overallDays = (int) ChronoUnit.DAYS.between(overallStart, overallEnd);
        if (overallDays < 1) overallDays = 1;

        FulfillmentMethod fulfillmentMethod = parseFulfillmentMethod(req.fulfillmentMethod());
        String pickupLocationName = null;
        BigDecimal pickupFee = BigDecimal.ZERO;
        boolean requiresAddress;

        if (req.pickupLocationId() != null) {
            PickupLocation location = pickupLocationRepository.findById(req.pickupLocationId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LOCATION_NOT_FOUND", "Không tìm thấy địa điểm nhận máy"));
            pickupLocationName = location.getName();
            pickupFee = location.getFee() != null ? location.getFee() : BigDecimal.ZERO;
            fulfillmentMethod = location.isDelivery() ? FulfillmentMethod.HOME_DELIVERY : FulfillmentMethod.PICKUP_AT_SHOP;
            requiresAddress = location.isDelivery();
        } else {
            requiresAddress = fulfillmentMethod == FulfillmentMethod.HOME_DELIVERY;
        }

        if (requiresAddress && (req.shippingAddress() == null || req.shippingAddress().isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SHIPPING_ADDRESS_REQUIRED",
                    "Vui lòng nhập địa chỉ nhận máy, hoặc chọn địa điểm không cần giao tận nơi");
        }

        total = total.add(pickupFee);

        Order order = Order.builder()
                .orderCode(generateOrderCode("DT"))
                .user(user)
                .orderType(OrderType.RENTAL)
                .status(OrderStatus.PENDING)
                .recipientName(req.recipientName())
                .recipientPhone(req.recipientPhone())
                .shippingAddress(requiresAddress ? req.shippingAddress() : null)
                .fulfillmentMethod(fulfillmentMethod)
                .pickupLocationName(pickupLocationName)
                .pickupFee(pickupFee)
                .note(req.note())
                .rentalStartDate(overallStart)
                .rentalEndDate(overallEnd)
                .rentalDays(overallDays)
                .subtotalAmount(subtotal)
                .promotionCode(appliedCode)
                .discountAmount(discountAmount)
                .loyaltyDiscountAmount(loyaltyDiscountAmount)
                .depositAmount(deposit)
                .totalAmount(total)
                .build();

        attachItems(order, orderItems);
        attachAddons(order, addonEntities);
        Order saved = orderRepository.save(order);

        if (!sourceCartItems.isEmpty()) {
            cartItemRepository.deleteAll(sourceCartItems);
        }

        emailService.sendOrderConfirmationEmail(user.getEmail(), new OrderEmailData(
                saved.getOrderCode(), true, saved.getRecipientName(), saved.getRecipientPhone(),
                fulfillmentMethod == FulfillmentMethod.PICKUP_AT_SHOP ? "Nhận tại shop" : saved.getShippingAddress(),
                saved.getRentalStartDate(), saved.getRentalEndDate(),
                saved.getRentalDays(), saved.getDepositAmount(), saved.getTotalAmount(),
                orderItems.stream()
                        .map(i -> new OrderEmailData.Line(i.getProductName(), i.getQuantity(), i.getSubtotal()))
                        .toList()
        ));
        notificationService.create(user.getId(), "Đặt thuê thành công",
                "Đơn thuê " + saved.getOrderCode() + " đã được ghi nhận, đang chờ xác nhận.",
                NotificationType.ORDER_UPDATE, saved.getId());
        notificationService.notifyAllAdmins("📷 Đơn thuê mới",
                "Đơn thuê " + saved.getOrderCode() + " của " + saved.getRecipientName() + " vừa được đặt, đang chờ xác nhận.",
                NotificationType.ORDER_UPDATE, saved.getId());

        return toDto(saved);
    }

    private FulfillmentMethod parseFulfillmentMethod(String raw) {
        if (raw == null || raw.isBlank()) return FulfillmentMethod.HOME_DELIVERY; // mặc định, tương thích ngược
        try {
            return FulfillmentMethod.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FULFILLMENT_METHOD",
                    "Cách nhận máy không hợp lệ: " + raw);
        }
    }

    // ------------------------------------------------------------------
    // Hợp đồng thuê điện tử (UC bổ sung): sau khi cọc được ghi nhận (status = CONFIRMED),
    // khách phải xem hợp đồng và ký tên trước khi đơn chính thức chuyển DEPOSIT_PAID.
    // ------------------------------------------------------------------

    /** Xem trước nội dung hợp đồng (chưa ký) — dùng để hiển thị cho khách trước khi bấm ký. */
    @Transactional(readOnly = true)
    public String getContractPreview(User user, Long orderId) {
        Order order = getOwnedRentalOrder(user, orderId);
        return buildContractText(order);
    }

    /** Khách ký hợp đồng (vẽ tay trên canvas) — PHẢI ký trước khi thanh toán cọc, nên yêu cầu đơn đang PENDING.
     *  Ký xong đơn vẫn ở PENDING (chưa nhận tiền); webhook thanh toán/khách trả tiền mặt sẽ tự chuyển tiếp. */
    @Transactional
    public OrderDto signRentalContract(User user, Long orderId, String signatureDataUrl) {
        Order order = getOwnedRentalOrder(user, orderId);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ORDER_NOT_PENDING",
                    "Chỉ có thể ký hợp đồng khi đơn đang chờ thanh toán (trạng thái hiện tại: "
                            + order.getStatus() + ")");
        }
        if (rentalContractRepository.existsByOrder_Id(order.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ALREADY_SIGNED", "Hợp đồng của đơn này đã được ký trước đó");
        }

        RentalContract contract = RentalContract.builder()
                .order(order)
                .contractText(buildContractText(order))
                .signatureDataUrl(signatureDataUrl)
                .build();
        rentalContractRepository.save(contract);

        notificationService.create(user.getId(), "Đã ký hợp đồng thuê",
                "Bạn đã ký hợp đồng cho đơn " + order.getOrderCode() + ". Vui lòng hoàn tất thanh toán cọc để shop chuẩn bị máy.",
                NotificationType.ORDER_UPDATE, order.getId());
        notificationService.notifyAllAdmins("📝 Khách vừa ký hợp đồng thuê",
                "Đơn " + order.getOrderCode() + " đã có chữ ký, đang chờ khách thanh toán cọc.",
                NotificationType.ORDER_UPDATE, order.getId());

        return toDto(order);
    }

    /** Xem lại hợp đồng đã ký (vd trong Hồ sơ / lịch sử đơn hàng). */
    @Transactional(readOnly = true)
    public RentalContractDto getRentalContract(User user, Long orderId) {
        Order order = getOwnedRentalOrder(user, orderId);
        return loadContractDto(order);
    }

    /** Admin xem hợp đồng đã ký của BẤT KỲ đơn thuê nào (không giới hạn theo chủ đơn) — để đối chiếu trước khi xác nhận/giao máy. */
    @Transactional(readOnly = true)
    public RentalContractDto getRentalContractForAdmin(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng"));
        if (order.getOrderType() != OrderType.RENTAL) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_RENTAL_ORDER", "Đơn này không phải đơn thuê");
        }
        return loadContractDto(order);
    }

    private RentalContractDto loadContractDto(Order order) {
        RentalContract contract = rentalContractRepository.findByOrder_Id(order.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONTRACT_NOT_FOUND",
                        "Đơn này chưa có hợp đồng được ký"));
        return new RentalContractDto(contract.getId(), order.getId(), contract.getContractText(),
                contract.getSignatureDataUrl(), contract.getSignedAt());
    }

    private Order getOwnedRentalOrder(User user, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng"));
        if (!order.getUser().getId().equals(user.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_YOUR_ORDER", "Đây không phải đơn hàng của bạn");
        }
        if (order.getOrderType() != OrderType.RENTAL) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_RENTAL_ORDER", "Đơn này không phải đơn thuê");
        }
        return order;
    }

    /**
     * Sinh nội dung hợp đồng thuê dạng text từ thông tin đơn — snapshot lại lúc ký để tránh
     * tranh chấp nếu mẫu hợp đồng thay đổi sau này.
     */
    /**
     * Sinh nội dung hợp đồng thuê dạng text từ thông tin đơn — snapshot lại lúc ký để tránh
     * tranh chấp nếu mẫu hợp đồng thay đổi sau này. Nội dung bám theo đúng quy trình thuê máy
     * chuẩn: (1) kiểm tra thiết bị lúc nhận, (2) đọc & ký hợp đồng, (3) thanh toán & nhận máy,
     * (4) trả máy & hoàn cọc — nêu rõ tiền thuê/cọc, quy định bồi thường, phạt trễ hạn, và trách
     * nhiệm khi máy tự phát sinh lỗi phần cứng.
     */
    private String buildContractText(Order order) {
        StringBuilder sb = new StringBuilder();
        sb.append("HỢP ĐỒNG THUÊ THIẾT BỊ\n");
        sb.append("Mã đơn: ").append(order.getOrderCode()).append("\n");
        sb.append("Bên thuê: ").append(order.getRecipientName())
                .append(" - SĐT: ").append(order.getRecipientPhone()).append("\n");
        sb.append("Thời hạn thuê: từ ").append(order.getRentalStartDate())
                .append(" đến ").append(order.getRentalEndDate())
                .append(" (").append(order.getRentalDays()).append(" ngày)\n");
        sb.append("Cách nhận máy: ").append(
                order.getFulfillmentMethod() == FulfillmentMethod.PICKUP_AT_SHOP
                        ? "Khách tự đến shop nhận và kiểm tra máy"
                        : "Shop giao máy tận nơi: " + order.getShippingAddress()
        ).append("\n\n");

        sb.append("Danh sách thiết bị:\n");
        for (OrderItem item : order.getItems()) {
            String slotLabel = switch (String.valueOf(item.getRentalSlot())) {
                case "MORNING" -> " (buổi Sáng)";
                case "AFTERNOON" -> " (buổi Chiều)";
                case "EVENING" -> " (buổi Tối)";
                default -> "";
            };
            sb.append("- ").append(item.getProductName())
                    .append(" x").append(item.getQuantity())
                    .append(slotLabel)
                    .append(": ").append(item.getSubtotal()).append("đ\n");
        }
        sb.append("\nTiền thuê: ").append(order.getSubtotalAmount()).append("đ\n");

        if (order.getAddons() != null && !order.getAddons().isEmpty()) {
            sb.append("\nPhụ kiện bổ sung:\n");
            for (OrderAddon addon : order.getAddons()) {
                sb.append("- ").append(addon.getName())
                        .append(addon.isIncluded() ? " (đi kèm miễn phí)" : ": " + addon.getPrice() + "đ")
                        .append("\n");
            }
        }

        sb.append("Tiền cọc thiết bị (30% giá trị thuê): ").append(order.getDepositAmount()).append("đ\n");
        sb.append("Tổng thanh toán (tiền thuê + cọc): ").append(order.getTotalAmount()).append("đ\n");

        sb.append("\n== QUY TRÌNH THUÊ MÁY ==\n\n");

        sb.append("Bước 1 — Kiểm tra thiết bị:\n");
        sb.append("Bên thuê xem xét kỹ ngoại hình và kiểm tra chức năng của máy ngay tại thời điểm nhận máy. ");
        sb.append("Nếu phát hiện vết xước, lỗi nhỏ hoặc bất thường nào có sẵn, bên thuê phải báo ngay cho shop để ghi nhận ");
        sb.append("(ghi chú tình trạng máy lúc giao). Sau khi đã nhận máy mà không báo, mặc định thiết bị ở tình trạng tốt.\n\n");

        sb.append("Bước 2 — Đọc và ký hợp đồng:\n");
        sb.append("Bên thuê xác nhận đã đọc và hiểu rõ toàn bộ điều khoản dưới đây trước khi ký. Chữ ký điện tử ở cuối văn bản này ");
        sb.append("có giá trị xác nhận sự đồng ý của bên thuê với các điều khoản đó.\n\n");

        sb.append("Bước 3 — Thanh toán và nhận máy:\n");
        sb.append("Chỉ sau khi ký hợp đồng, bên thuê mới tiến hành thanh toán tiền cọc và tiền thuê (qua chuyển khoản hoặc tiền mặt tuỳ ");
        sb.append("phương thức đã chọn). Bên thuê nên giữ lại bản hợp đồng này (hoặc ảnh chụp) trong suốt thời gian thuê.\n\n");

        sb.append("Bước 4 — Trả máy và hoàn cọc:\n");
        sb.append("Khi hết hạn thuê, bên thuê mang máy đến trả (hoặc shop đến thu, tuỳ thoả thuận). Shop kiểm tra lại tình trạng máy ");
        sb.append("(đối chiếu với tình trạng đã ghi nhận ở Bước 1). Nếu máy còn nguyên vẹn và trả đúng hạn, shop hoàn trả 100% tiền cọc ");
        sb.append("cho bên thuê ngay sau khi kiểm tra xong.\n\n");

        sb.append("== ĐIỀU KHOẢN CHI TIẾT ==\n\n");

        sb.append("1. Quy định bồi thường: Nếu thiết bị bị hư hỏng, trầy xước ngoài tình trạng đã ghi nhận lúc giao, hoặc bị mất, ");
        sb.append("bên thuê có trách nhiệm bồi thường theo mức thiệt hại thực tế (đối chiếu giá trị sửa chữa/giá trị thiết bị). ");
        sb.append("Số tiền bồi thường được trừ trước vào tiền cọc; nếu vượt quá tiền cọc, bên thuê thanh toán phần chênh lệch còn lại.\n\n");

        sb.append("2. Quy định phạt trễ hạn: Nếu bên thuê trả máy trễ so với thời hạn ghi ở trên (Bước 4), shop sẽ tính phụ phí trễ hạn ");
        sb.append("theo đơn giá thuê tương ứng cho mỗi ngày/buổi trễ, thoả thuận trực tiếp tại thời điểm trả máy và được trừ vào tiền cọc trước khi hoàn lại phần còn dư.\n\n");

        sb.append("3. Trách nhiệm khi máy tự phát sinh lỗi phần cứng: Nếu trong thời gian thuê, thiết bị phát sinh lỗi phần cứng không do ");
        sb.append("tác động từ bên thuê (lỗi kỹ thuật, hao mòn tự nhiên...), bên thuê không phải chịu trách nhiệm bồi thường cho lỗi đó, ");
        sb.append("nhưng cần báo ngay cho shop để được hỗ trợ đổi máy hoặc xử lý phù hợp. Trường hợp có tranh chấp về nguyên nhân hư hỏng, ");
        sb.append("hai bên cùng đối chiếu tình trạng máy đã ghi nhận ở Bước 1 để xác định trách nhiệm.\n\n");

        sb.append("4. Tiền cọc chỉ được hoàn lại đầy đủ (100%) nếu thiết bị được trả đúng hạn và đúng tình trạng lúc nhận (theo Bước 1). ");
        sb.append("Mọi khoản trừ cọc (nếu có) đều được shop thông báo rõ lý do và số tiền cụ thể cho bên thuê.\n\n");

        sb.append("5. Chữ ký điện tử dưới đây có giá trị pháp lý tương đương chữ ký tay, xác nhận bên thuê đã đọc, hiểu rõ và đồng ý ");
        sb.append("với toàn bộ nội dung hợp đồng này.\n");

        return sb.toString();
    }

    private OrderItem buildRentalOrderItem(Product product, int quantity, LocalDate start, LocalDate end, String rentalSlot) {
        if (Boolean.FALSE.equals(product.getIsAvailable())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRODUCT_UNAVAILABLE",
                    "Sản phẩm \"" + product.getName() + "\" hiện không khả dụng");
        }
        if (quantity < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY", "Số lượng phải lớn hơn 0");
        }

        // Lấy stock rental nếu có, nếu không dùng stock chung
        Integer rentalStock = product.getRentalStockQuantity() != null
                ? product.getRentalStockQuantity()
                : product.getStockQuantity();

        // Không chỉ so tồn kho hiện tại — vì tồn kho chỉ bị trừ lúc giao máy (DELIVERED), cần cộng thêm
        // số lượng đang được giữ chỗ bởi các đơn thuê khác có khoảng ngày trùng với đơn đang đặt.
        int alreadyReserved = countOverlappingRentalQuantity(product.getId(), start, end, null);
        int availableForRange = rentalStock - alreadyReserved;
        if (quantity > availableForRange) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OUT_OF_STOCK",
                    "Sản phẩm \"" + product.getName() + "\" chỉ còn " + Math.max(availableForRange, 0)
                            + " máy trống trong khoảng " + start + " → " + end);
        }

        int days = (int) ChronoUnit.DAYS.between(start, end);
        if (days < 1) days = 1;

        BigDecimal unitPrice;
        String normalizedSlot = null;
        if (rentalSlot != null && !rentalSlot.isBlank()) {
            if (!start.isEqual(end)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RENTAL_SLOT",
                        "Thuê theo buổi chỉ áp dụng cho 1 ngày — ngày nhận và ngày trả phải trùng nhau");
            }
            normalizedSlot = rentalSlot.trim().toUpperCase();
            BigDecimal slotPrice = switch (normalizedSlot) {
                case "MORNING" -> product.getRentPriceMorning();
                case "AFTERNOON" -> product.getRentPriceAfternoon();
                case "EVENING" -> product.getRentPriceEvening();
                default -> throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RENTAL_SLOT",
                        "Buổi thuê không hợp lệ: " + rentalSlot);
            };
            if (slotPrice == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "RENTAL_SLOT_NOT_CONFIGURED",
                        "Sản phẩm \"" + product.getName() + "\" chưa có giá cho buổi này");
            }
            unitPrice = slotPrice;
            days = 1; // 1 buổi luôn tính là 1 ngày trong lịch, không nhân thêm theo ngày
        } else {
            unitPrice = product.getRentPrice();
            if (unitPrice == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "RENTAL_PRICE_NOT_CONFIGURED",
                        "Sản phẩm \"" + product.getName() + "\" chưa được thiết lập giá thuê theo ngày");
            }
        }

        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        if (normalizedSlot == null) {
            subtotal = subtotal.multiply(BigDecimal.valueOf(days));
        }

        return OrderItem.builder()
                .product(product)
                .productName(product.getName())
                .productImageUrl(product.getImageUrl())
                .unitPrice(unitPrice)
                .quantity(quantity)
                .rentalDays(days)
                .rentalSlot(normalizedSlot)
                .subtotal(subtotal)
                .build();
    }

    // ------------------------------------------------------------------
    // Lịch trống công khai (khách xem trước khi đặt) — UC bổ sung
    // ------------------------------------------------------------------

    /**
     * Với mỗi ngày trong [from, to], trả về số máy còn trống của sản phẩm và các buổi (nếu có) đã bị đặt.
     * Dùng để tô màu lịch chọn ngày ở trang đặt thuê — khách thấy ngay ngày nào hết máy thay vì bấm thử.
     */
    @Transactional(readOnly = true)
    public List<DayAvailabilityDto> getProductAvailability(Long productId, LocalDate from, LocalDate to) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Sản phẩm không tồn tại"));
        if (to.isBefore(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "Khoảng ngày không hợp lệ");
        }

        List<OrderItem> items = orderItemRepository.findByProduct_IdAndOrder_OrderTypeAndOrder_StatusIn(
                productId, OrderType.RENTAL, ACTIVE_RENTAL_STATUSES);

        List<DayAvailabilityDto> result = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            int reserved = 0;
            Set<String> bookedSlots = new java.util.HashSet<>();
            for (OrderItem item : items) {
                Order order = item.getOrder();
                if (datesOverlap(order.getRentalStartDate(), order.getRentalEndDate(), day, day)) {
                    reserved += item.getQuantity();
                    if (item.getRentalSlot() != null) bookedSlots.add(item.getRentalSlot());
                }
            }
            int remaining = Math.max(product.getStockQuantity() - reserved, 0);
            result.add(new DayAvailabilityDto(day, reserved, remaining, new ArrayList<>(bookedSlots)));
        }
        return result;
    }

    /**
     * Tổng số lượng sản phẩm đang được giữ chỗ bởi các đơn thuê khác (trạng thái còn hiệu lực)
     * có khoảng ngày thuê giao với [start, end]. excludeOrderId dùng khi kiểm tra lại 1 đơn đã có sẵn.
     */
    private int countOverlappingRentalQuantity(Long productId, LocalDate start, LocalDate end, Long excludeOrderId) {
        List<OrderItem> items = orderItemRepository.findByProduct_IdAndOrder_OrderTypeAndOrder_StatusIn(
                productId, OrderType.RENTAL, ACTIVE_RENTAL_STATUSES);

        int total = 0;
        for (OrderItem item : items) {
            Order order = item.getOrder();
            if (excludeOrderId != null && order.getId().equals(excludeOrderId)) continue;
            if (datesOverlap(order.getRentalStartDate(), order.getRentalEndDate(), start, end)) {
                total += item.getQuantity();
            }
        }
        return total;
    }

    // ------------------------------------------------------------------
    // Giám sát tồn kho (Admin) — UC bổ sung: tách rõ "tổng kho / đang giữ chỗ thuê / có thể bán"
    // ------------------------------------------------------------------

    /**
     * Trả về, cho MỌI sản phẩm, 3 con số: tổng kho, số máy đang bị đơn thuê tương lai giữ chỗ (đỉnh
     * cộng dồn), và số máy còn có thể bán ngay. Dùng cho trang quản trị sản phẩm để admin biết rõ vì sao
     * một sản phẩm bị chặn bán dù nhìn tổng kho vẫn còn hàng.
     */
    @Transactional(readOnly = true)
    public List<ProductStockBreakdownDto> getStockBreakdownForAllProducts() {
        return productRepository.findAll().stream()
                .map(p -> {
                    int reserved = maxConcurrentFutureRentalQuantity(p.getId());
                    int available = Math.max(p.getStockQuantity() - reserved, 0);
                    return new ProductStockBreakdownDto(p.getId(), p.getName(), p.getStockQuantity(), reserved, available);
                })
                .toList();
    }

    /**
     * Số máy tối đa bị giữ chỗ CÙNG LÚC bởi các đơn thuê còn hiệu lực, tính từ hôm nay trở đi.
     * Dùng để chặn bán mất máy đã hứa giao cho người thuê trong tương lai — kể cả khi các lượt thuê
     * đó không trùng ngày nhau (mỗi lượt vẫn cần máy tồn tại vật lý, chỉ là không cùng lúc).
     * Kỹ thuật: sweep-line — mỗi đơn thuê tạo 1 sự kiện +quantity lúc bắt đầu và -quantity lúc kết thúc,
     * sau đó quét theo thời gian để tìm đỉnh cộng dồn.
     */
    private int maxConcurrentFutureRentalQuantity(Long productId) {
        LocalDate today = LocalDate.now();
        List<OrderItem> items = orderItemRepository.findByProduct_IdAndOrder_OrderTypeAndOrder_StatusIn(
                productId, OrderType.RENTAL, ACTIVE_RENTAL_STATUSES);

        List<int[]> events = new ArrayList<>(); // [ngày-số-thứ-tự-từ-epoch, +/-quantity]
        for (OrderItem item : items) {
            Order order = item.getOrder();
            LocalDate start = order.getRentalStartDate();
            LocalDate end = order.getRentalEndDate();
            if (start == null || end == null || end.isBefore(today)) continue; // đơn thuê đã kết thúc, bỏ qua

            LocalDate clampedStart = start.isBefore(today) ? today : start;
            events.add(new int[]{(int) clampedStart.toEpochDay(), item.getQuantity()});
            events.add(new int[]{(int) end.toEpochDay() + 1, -item.getQuantity()});
        }

        // Cùng ngày: xử lý sự kiện kết thúc (-quantity) trước sự kiện bắt đầu (+quantity),
        // vì máy trả xong ngay hôm đó có thể dùng lại cho lượt thuê mới bắt đầu cùng ngày.
        events.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));

        int running = 0;
        int peak = 0;
        for (int[] e : events) {
            running += e[1];
            if (running > peak) peak = running;
        }
        return peak;
    }

    /**
     * Tồn kho thuê tại 1 ngày cụ thể, cho MỌI sản phẩm: tổng kho thuê, số máy đang được thuê giao/trùng
     * ngày đó, số máy còn trống, và các buổi (Sáng/Chiều/Tối) đã có người đặt. Dùng cho trang admin
     * "Kiểm soát tồn kho thuê" để xem nhanh theo ngày.
     */
    @Transactional(readOnly = true)
    public List<RentalInventoryEntryDto> getRentalInventory(LocalDate date) {
        return productRepository.findAll().stream()
                .map(p -> {
                    int stock = p.getRentalStockQuantity() != null ? p.getRentalStockQuantity() : p.getStockQuantity();

                    List<OrderItem> items = orderItemRepository.findByProduct_IdAndOrder_OrderTypeAndOrder_StatusIn(
                            p.getId(), OrderType.RENTAL, ACTIVE_RENTAL_STATUSES);

                    int reserved = 0;
                    Set<String> bookedSlots = new java.util.HashSet<>();
                    for (OrderItem item : items) {
                        Order order = item.getOrder();
                        if (datesOverlap(order.getRentalStartDate(), order.getRentalEndDate(), date, date)) {
                            reserved += item.getQuantity();
                            if (item.getRentalSlot() != null) bookedSlots.add(item.getRentalSlot());
                        }
                    }

                    int available = Math.max(stock - reserved, 0);
                    return new RentalInventoryEntryDto(
                            p.getId(), p.getName(), p.getImageUrl(), stock, reserved, available,
                            new ArrayList<>(bookedSlots));
                })
                .toList();
    }

    private boolean datesOverlap(LocalDate aStart, LocalDate aEnd, LocalDate bStart, LocalDate bEnd) {
        if (aStart == null || aEnd == null || bStart == null || bEnd == null) return false;
        return !aEnd.isBefore(bStart) && !bEnd.isBefore(aStart);
    }

    private void validateRentalDates(LocalDate start, LocalDate end, boolean isSlotBooking) {
        if (start == null || end == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RENTAL_DATES_REQUIRED", "Vui lòng chọn ngày bắt đầu và kết thúc thuê");
        }
        // Thuê theo buổi (Sáng/Chiều/Tối) luôn là thuê trong đúng 1 ngày -> start == end là hợp lệ.
        // Thuê theo ngày (khoảng ngày) thì end bắt buộc phải sau start.
        if (isSlotBooking ? end.isBefore(start) : !end.isAfter(start)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RENTAL_RANGE",
                    isSlotBooking
                            ? "Ngày kết thúc thuê không được trước ngày bắt đầu"
                            : "Ngày kết thúc thuê phải sau ngày bắt đầu");
        }
        if (start.isBefore(LocalDate.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RENTAL_RANGE", "Ngày bắt đầu thuê không được ở quá khứ");
        }
    }

    // ------------------------------------------------------------------
    // Tra cứu / quản lý đơn hàng
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<OrderDto> getMyOrders(User user, OrderType orderType) {
        List<Order> orders = orderType != null
                ? orderRepository.findByUserIdAndOrderTypeOrderByCreatedAtDesc(user.getId(), orderType)
                : orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        return orders.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public OrderDto getMyOrderById(User user, Long orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng"));
        return toDto(order);
    }

    /** Lấy entity gốc (không phải DTO) để xuất hoá đơn PDF — chỉ chủ đơn mới xem được. */
    @Transactional(readOnly = true)
    public Order getMyOrderEntity(User user, Long orderId) {
        return orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng"));
    }

    /** Lấy entity gốc để xuất hoá đơn PDF — dành cho admin, xem được mọi đơn. */
    @Transactional(readOnly = true)
    public Order getOrderEntity(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng"));
    }

    @Transactional
    public OrderDto cancelMyOrder(User user, Long orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng"));

        // Chỉ cho khách tự huỷ khi CÒN CHƯA thanh toán (PENDING). Một khi đã CONFIRMED — nghĩa là
        // webhook SePay đã ghi nhận tiền vào (xem PaymentWebhookController) — tuyệt đối không cho tự
        // huỷ nữa, kể cả khi khách bấm đúng lúc trạng thái vừa chuyển (race condition với polling ở FE):
        // nếu không sẽ mất tiền oan cho khách (tiền đã vào nhưng đơn lại bị huỷ). Từ CONFIRMED trở đi,
        // muốn huỷ phải qua admin để xử lý hoàn tiền đúng quy trình.
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ORDER_NOT_CANCELLABLE",
                    "Đơn hàng đang ở trạng thái \"" + order.getStatus() + "\" nên không thể tự huỷ — " +
                            "nếu đã chuyển khoản, vui lòng liên hệ shop để được hỗ trợ.");
        }

        if (order.getOrderType() == OrderType.PURCHASE) {
            // Hoàn lại tồn kho khi huỷ đơn mua.
            for (OrderItem item : order.getItems()) {
                if (item.getProduct() != null) {
                    Product product = item.getProduct();
                    product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                    product.setIsAvailable(true);
                    productRepository.save(product);
                }
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);

        notificationService.create(user.getId(), "Đơn hàng đã huỷ",
                "Đơn " + saved.getOrderCode() + " đã được huỷ theo yêu cầu của bạn.",
                NotificationType.ORDER_UPDATE, saved.getId());
        notificationService.notifyAllAdmins("❌ Khách huỷ đơn",
                "Đơn " + saved.getOrderCode() + " của " + saved.getRecipientName() + " vừa bị khách tự huỷ (chưa thanh toán).",
                NotificationType.ORDER_UPDATE, saved.getId());

        return toDto(saved);
    }

    // ------------------------------------------------------------------
    // Admin
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<OrderDto> getAllOrders(OrderType orderType) {
        List<Order> orders = orderType != null
                ? orderRepository.findByOrderTypeOrderByCreatedAtDesc(orderType)
                : orderRepository.findAllByOrderByCreatedAtDesc();
        return orders.stream().map(this::toDto).toList();
    }

    @Transactional
    public OrderDto updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng"));

        Set<OrderStatus> finalizedStatuses = EnumSet.of(
                OrderStatus.CANCELLED, OrderStatus.COMPLETED, OrderStatus.RETURNED, OrderStatus.RETURN_REQUESTED,
                OrderStatus.RENTAL_RETURN_REQUESTED, OrderStatus.DEPOSIT_PAID, OrderStatus.DELIVERED, OrderStatus.RENTAL_RETURNED,
                OrderStatus.INSPECTED, OrderStatus.DISPUTED);
        if (finalizedStatuses.contains(order.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ORDER_ALREADY_FINALIZED",
                    "Đơn hàng đang ở trạng thái \"" + order.getStatus() + "\", vui lòng dùng chức năng tương ứng cho trạng thái này");
        }

        if (newStatus == OrderStatus.RETURN_REQUESTED || newStatus == OrderStatus.RETURNED
                || RENTAL_FLOW_ONLY_STATUSES.contains(newStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS_TRANSITION",
                    "Vui lòng dùng API riêng cho trạng thái này");
        }

        if (order.getOrderType() == OrderType.RENTAL
                && newStatus != OrderStatus.CONFIRMED && newStatus != OrderStatus.CANCELLED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS_TRANSITION",
                    "Đơn thuê chỉ chuyển được sang \"Đã xác nhận\" hoặc \"Huỷ\" qua API này; "
                            + "các bước tiếp theo (cọc, giao máy, trả máy, kiểm tra) dùng API riêng cho quy trình thuê");
        }

        if (newStatus == OrderStatus.CANCELLED && order.getOrderType() == OrderType.PURCHASE) {
            for (OrderItem item : order.getItems()) {
                if (item.getProduct() != null) {
                    Product product = item.getProduct();
                    product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                    product.setIsAvailable(true);
                    productRepository.save(product);
                }
            }
        }
        // Lưu ý: đơn thuê bị huỷ ở PENDING/CONFIRMED chưa từng bị trừ kho (kho chỉ trừ lúc DELIVERED),
        // nên không cần hoàn kho ở đây.

        if (newStatus == OrderStatus.COMPLETED) {
            order.setCompletedAt(Instant.now());
        }

        order.setStatus(newStatus);
        Order saved = orderRepository.save(order);

        notificationService.create(order.getUser().getId(), STATUS_NOTIFICATION_TITLE.getOrDefault(newStatus, "Cập nhật đơn hàng"),
                "Đơn " + order.getOrderCode() + " " + STATUS_NOTIFICATION_BODY.getOrDefault(newStatus, "vừa được cập nhật trạng thái."),
                NotificationType.ORDER_UPDATE, order.getId());

        if (newStatus == OrderStatus.CONFIRMED) {
            notificationService.notifyAllAdmins("💰 Đã nhận thanh toán",
                    "Đơn " + order.getOrderCode() + " vừa được xác nhận đã thanh toán — chuẩn bị máy/hàng cho khách.",
                    NotificationType.ORDER_UPDATE, order.getId());
        }

        return toDto(saved);
    }

    // ------------------------------------------------------------------
    // Quy trình thuê (rental lifecycle) — UC bổ sung: cọc, giao máy, trả máy, kiểm tra
    // ------------------------------------------------------------------

    /** Bước 3: Nhân viên xác nhận đã nhận cọc từ khách (CONFIRMED -> DEPOSIT_PAID). */
    @Transactional
    public OrderDto markDepositPaid(Long orderId) {
        Order order = getRentalOrderOrThrow(orderId);
        requireRentalStatus(order, OrderStatus.CONFIRMED,
                "Chỉ có thể xác nhận đã nhận cọc khi đơn đang ở trạng thái \"Đã xác nhận\"");

        order.setStatus(OrderStatus.DEPOSIT_PAID);
        order.setDepositPaidAt(Instant.now());
        Order saved = orderRepository.save(order);
        notificationService.create(order.getUser().getId(), "Đã ghi nhận tiền cọc",
                "Đơn thuê " + order.getOrderCode() + " đã ghi nhận cọc, đang chuẩn bị giao máy.",
                NotificationType.ORDER_UPDATE, order.getId());
        return toDto(saved);
    }

    /** Bước 4: Giao thiết bị cho khách, ghi nhận tình trạng máy lúc giao (DEPOSIT_PAID -> DELIVERED). Trừ kho vì máy đã rời cửa hàng. */
    @Transactional
    public OrderDto markDelivered(Long orderId, String conditionNote) {
        Order order = getRentalOrderOrThrow(orderId);
        requireRentalStatus(order, OrderStatus.DEPOSIT_PAID,
                "Chỉ có thể giao thiết bị sau khi đã nhận cọc");

        for (OrderItem item : order.getItems()) {
            if (item.getProduct() != null) {
                Product product = item.getProduct();
                int newStock = Math.max(product.getStockQuantity() - item.getQuantity(), 0);
                product.setStockQuantity(newStock);
                if (newStock <= 0) {
                    product.setIsAvailable(false);
                }
                productRepository.save(product);
            }
        }

        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(Instant.now());
        order.setDeliveryConditionNote(conditionNote);
        Order saved = orderRepository.save(order);
        notificationService.create(order.getUser().getId(), "Đã giao thiết bị",
                "Đơn thuê " + order.getOrderCode() + " đã giao máy. Nhớ trả đúng hạn (" + order.getRentalEndDate() + ") nhé!",
                NotificationType.ORDER_UPDATE, order.getId());
        return toDto(saved);
    }

    /** Bước 6: Ghi nhận khách đã trả thiết bị (DELIVERED -> RENTAL_RETURNED), chờ kiểm tra tình trạng. */
    @Transactional
    public OrderDto markRentalReturned(Long orderId) {
        Order order = getRentalOrderOrThrow(orderId);
        requireRentalStatus(order, OrderStatus.DELIVERED,
                "Chỉ có thể ghi nhận trả máy khi đơn đang trong thời gian thuê (đã giao máy)");

        order.setStatus(OrderStatus.RENTAL_RETURNED);
        order.setReturnedAt(Instant.now());
        Order saved = orderRepository.save(order);
        notificationService.create(order.getUser().getId(), "Đã ghi nhận trả máy",
                "Đơn thuê " + order.getOrderCode() + " đang được kiểm tra tình trạng trước khi hoàn cọc.",
                NotificationType.ORDER_UPDATE, order.getId());
        return toDto(saved);
    }

    /**
     * Bước 7: Nhân viên kho kiểm tra tình trạng máy lúc nhận lại (RENTAL_RETURNED -> INSPECTED).
     * Hoàn kho ngay ở bước này vì máy đã thực sự về lại cửa hàng, không phụ thuộc vào quyết định
     * hoàn/trừ cọc ở bước sau. INSPECTED là trạng thái thật (được lưu), không tự động chuyển tiếp -
     * admin sẽ chủ động chọn "Hoàn cọc" hoặc "Trừ cọc" ở bước 8a/8b.
     */
    @Transactional
    public OrderDto inspectRentalReturn(Long orderId, String inspectionNote) {
        Order order = getRentalOrderOrThrow(orderId);
        requireRentalStatus(order, OrderStatus.RENTAL_RETURNED,
                "Chỉ có thể kiểm tra tình trạng máy sau khi đã ghi nhận khách trả máy");

        for (OrderItem item : order.getItems()) {
            if (item.getProduct() != null) {
                Product product = item.getProduct();
                product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                product.setIsAvailable(true);
                productRepository.save(product);
            }
        }

        order.setStatus(OrderStatus.INSPECTED);
        order.setInspectedAt(Instant.now());
        order.setInspectionNote(inspectionNote);

        Order saved = orderRepository.save(order);
        return toDto(saved);
    }

    /** Bước 8a: không phát sinh hư hỏng/trễ hạn -> hoàn đủ cọc (INSPECTED -> COMPLETED). */
    @Transactional
    public OrderDto refundDeposit(Long orderId) {
        Order order = getRentalOrderOrThrow(orderId);
        requireRentalStatus(order, OrderStatus.INSPECTED,
                "Chỉ có thể hoàn cọc sau khi đã kiểm tra tình trạng máy");

        order.setDamageAmount(BigDecimal.ZERO);
        order.setRefundAmount(order.getDepositAmount());
        order.setStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(Instant.now());

        Order saved = orderRepository.save(order);
        notificationService.create(order.getUser().getId(), "Đã hoàn cọc",
                "Đơn thuê " + order.getOrderCode() + " đã hoàn tất, hoàn đủ " + formatVnd(order.getRefundAmount()) + " tiền cọc.",
                NotificationType.ORDER_UPDATE, order.getId());
        return toDto(saved);
    }

    /** Bước 8b: có hư hỏng/trễ hạn -> trừ một phần cọc (INSPECTED -> DISPUTED). */
    @Transactional
    public OrderDto deductDeposit(Long orderId, BigDecimal damageAmount, String disputeReason) {
        Order order = getRentalOrderOrThrow(orderId);
        requireRentalStatus(order, OrderStatus.INSPECTED,
                "Chỉ có thể trừ cọc sau khi đã kiểm tra tình trạng máy");

        if (damageAmount == null || damageAmount.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DAMAGE_AMOUNT_REQUIRED",
                    "Vui lòng nhập số tiền trừ cọc do hư hỏng/trễ hạn");
        }
        if (disputeReason == null || disputeReason.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DISPUTE_REASON_REQUIRED",
                    "Vui lòng nhập lý do tranh chấp (hư hỏng/trễ hạn...)");
        }

        BigDecimal cappedDamage = damageAmount.min(order.getDepositAmount());
        order.setDamageAmount(cappedDamage);
        order.setDisputeReason(disputeReason);
        order.setRefundAmount(order.getDepositAmount().subtract(cappedDamage));
        order.setStatus(OrderStatus.DISPUTED);
        order.setCompletedAt(Instant.now());

        Order saved = orderRepository.save(order);
        notificationService.create(order.getUser().getId(), "Đã trừ cọc do phát sinh vấn đề",
                "Đơn thuê " + order.getOrderCode() + " bị trừ " + formatVnd(cappedDamage) + " cọc. Lý do: " + disputeReason,
                NotificationType.ORDER_UPDATE, order.getId());
        return toDto(saved);
    }

    /**
     * UC: PUT /api/orders/{orderId}/extend — khách tự gia hạn thời gian thuê khi đang trong thời
     * gian thuê (DELIVERED). Kiểm tra lại trùng lịch cho đúng khoảng ngày gia hạn (loại trừ chính
     * đơn này), cộng thêm tiền thuê cho số ngày gia hạn vào subtotal/total (giữ nguyên tiền cọc).
     */
    @Transactional
    public OrderDto extendRental(User user, Long orderId, LocalDate newEndDate) {
        Order order = orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng"));

        if (order.getOrderType() != OrderType.RENTAL) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_RENTAL_ORDER", "Thao tác này chỉ áp dụng cho đơn thuê");
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS_TRANSITION",
                    "Chỉ có thể gia hạn khi đơn đang trong thời gian thuê (đã giao máy, chưa trả)");
        }
        if (newEndDate == null || !newEndDate.isAfter(order.getRentalEndDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE",
                    "Ngày gia hạn phải sau ngày kết thúc hiện tại (" + order.getRentalEndDate() + ")");
        }

        LocalDate extensionStart = order.getRentalEndDate().plusDays(1);
        int extraDays = (int) ChronoUnit.DAYS.between(order.getRentalEndDate(), newEndDate);
        BigDecimal extraCost = BigDecimal.ZERO;

        // Kiểm tra còn trống cho khoảng ngày gia hạn (loại trừ chính đơn đang gia hạn này).
        for (OrderItem item : order.getItems()) {
            if (item.getProduct() == null) continue;
            Product product = item.getProduct();
            int alreadyReserved = countOverlappingRentalQuantity(product.getId(), extensionStart, newEndDate, order.getId());
            int availableForRange = product.getStockQuantity() - alreadyReserved;
            if (item.getQuantity() > availableForRange) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "OUT_OF_STOCK",
                        "Sản phẩm \"" + item.getProductName() + "\" đã có khách khác đặt trong khoảng ngày gia hạn, không thể gia hạn");
            }
            extraCost = extraCost.add(item.getUnitPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity()))
                    .multiply(BigDecimal.valueOf(extraDays)));
        }

        for (OrderItem item : order.getItems()) {
            int newItemDays = item.getRentalDays() + extraDays;
            item.setRentalDays(newItemDays);
            item.setSubtotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())).multiply(BigDecimal.valueOf(newItemDays)));
        }

        order.setRentalEndDate(newEndDate);
        order.setRentalDays(order.getRentalDays() + extraDays);
        order.setSubtotalAmount(order.getSubtotalAmount().add(extraCost));
        order.setTotalAmount(order.getTotalAmount().add(extraCost));
        // Reset để job nhắc hạn trả máy gửi lại email cho hạn mới thay vì im lặng vì đã "gửi rồi".
        order.setDueReminderSentAt(null);

        Order saved = orderRepository.save(order);
        notificationService.create(user.getId(), "Gia hạn thuê thành công",
                "Đơn thuê " + order.getOrderCode() + " đã gia hạn tới ngày " + newEndDate + ".",
                NotificationType.ORDER_UPDATE, order.getId());
        return toDto(saved);
    }

    private Order getRentalOrderOrThrow(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng"));
        if (order.getOrderType() != OrderType.RENTAL) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_RENTAL_ORDER", "Thao tác này chỉ áp dụng cho đơn thuê");
        }
        return order;
    }

    private void requireRentalStatus(Order order, OrderStatus expected, String message) {
        if (order.getStatus() != expected) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS_TRANSITION",
                    message + " (trạng thái hiện tại: " + order.getStatus() + ")");
        }
    }

    private String formatVnd(BigDecimal amount) {
        if (amount == null) amount = BigDecimal.ZERO;
        return java.text.NumberFormat.getInstance(new java.util.Locale("vi", "VN")).format(amount) + "đ";
    }

    /** UC: GET /api/orders/admin/rental-calendar — dữ liệu lịch thuê cho khoảng ngày [from, to], gộp sẵn ở backend thay vì FE tự tổng hợp toàn bộ đơn hàng. */
    @Transactional(readOnly = true)
    public List<RentalCalendarEntryDto> getRentalCalendar(LocalDate from, LocalDate to) {
        List<Order> orders = orderRepository.findByOrderTypeOrderByCreatedAtDesc(OrderType.RENTAL).stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .filter(o -> o.getRentalStartDate() != null && o.getRentalEndDate() != null)
                .filter(o -> !o.getRentalEndDate().isBefore(from) && !o.getRentalStartDate().isAfter(to))
                .toList();

        List<RentalCalendarEntryDto> result = new ArrayList<>();
        for (Order o : orders) {
            for (OrderItem item : o.getItems()) {
                if (item.getProduct() == null) continue;
                result.add(new RentalCalendarEntryDto(
                        item.getProduct().getId(),
                        item.getProductName(),
                        item.getProductImageUrl(),
                        item.getProduct().getStockQuantity(),
                        item.getProduct().getBrand(),
                        o.getId(),
                        o.getOrderCode(),
                        o.getRecipientName(),
                        o.getRecipientPhone(),
                        item.getQuantity(),
                        o.getStatus().name(),
                        item.getRentalSlot(),
                        o.getRentalStartDate(),
                        o.getRentalEndDate()
                ));
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Đổi trả / hoàn tiền đơn mua (UC-25)
    // ------------------------------------------------------------------

    @Transactional
    public OrderDto requestReturn(User user, Long orderId, String reason, List<String> imageUrls) {
        Order order = orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng"));

        if (order.getOrderType() == OrderType.PURCHASE) {
            if (order.getStatus() != OrderStatus.COMPLETED || order.getCompletedAt() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "ORDER_NOT_COMPLETED",
                        "Chỉ có thể yêu cầu đổi trả sau khi đơn hàng đã giao xong");
            }

            long daysSinceCompleted = ChronoUnit.DAYS.between(order.getCompletedAt(), Instant.now());
            if (daysSinceCompleted > RETURN_WINDOW_DAYS) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "RETURN_WINDOW_EXPIRED",
                        "Đã quá " + RETURN_WINDOW_DAYS + " ngày kể từ khi nhận hàng, không thể yêu cầu đổi trả");
            }
            order.setStatus(OrderStatus.RETURN_REQUESTED);
            order.setReturnReason(reason);
            order.setReturnImageUrls(
                    imageUrls != null && !imageUrls.isEmpty()
                            ? String.join(",", imageUrls.stream().filter(u -> u != null && !u.isBlank()).toList())
                            : null
            );
            order.setReturnRequestedAt(Instant.now());
            Order saved = orderRepository.save(order);
            notificationService.notifyAllAdmins("↩️ Yêu cầu đổi trả mới",
                    "Đơn " + saved.getOrderCode() + " vừa được yêu cầu đổi trả: " + reason,
                    NotificationType.ORDER_UPDATE, saved.getId());
            return toDto(saved);
        }

        if (order.getOrderType() == OrderType.RENTAL) {
            if (order.getStatus() != OrderStatus.DELIVERED) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RETURN_REQUEST",
                        "Chỉ có thể yêu cầu trả máy khi đơn thuê đang trong thời gian thuê");
            }
            order.setStatus(OrderStatus.RENTAL_RETURN_REQUESTED);
            order.setReturnReason(reason);
            order.setReturnImageUrls(
                    imageUrls != null && !imageUrls.isEmpty()
                            ? String.join(",", imageUrls.stream().filter(u -> u != null && !u.isBlank()).toList())
                            : null
            );
            order.setReturnRequestedAt(Instant.now());
            order.setReturnRejectReason(null);
            Order saved = orderRepository.save(order);
            notificationService.notifyAllAdmins("↩️ Yêu cầu trả máy mới",
                    "Đơn thuê " + saved.getOrderCode() + " đang yêu cầu trả máy: " + reason,
                    NotificationType.ORDER_UPDATE, saved.getId());
            return toDto(saved);
        }

        throw new ApiException(HttpStatus.BAD_REQUEST, "RETURN_NOT_SUPPORTED", "Chỉ đơn mua hoặc đơn thuê mới có thể yêu cầu trả");
    }

    @Transactional
    public OrderDto approveReturn(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng"));

        if (order.getStatus() == OrderStatus.RETURN_REQUESTED) {
            // Hoàn lại tồn kho vì hàng được trả về.
            for (OrderItem item : order.getItems()) {
                if (item.getProduct() != null) {
                    Product product = item.getProduct();
                    product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                    product.setIsAvailable(true);
                    productRepository.save(product);
                }
            }

            order.setStatus(OrderStatus.RETURNED);
            order.setReturnedAt(Instant.now());
            order.setRefundAmount(order.getTotalAmount());
            Order saved = orderRepository.save(order);
            notificationService.create(order.getUser().getId(), "Yêu cầu đổi trả đã được duyệt",
                    "Đơn " + order.getOrderCode() + " đã được hoàn " + formatVnd(order.getRefundAmount()) + ".",
                    NotificationType.ORDER_UPDATE, order.getId());
            return toDto(saved);
        }

        if (order.getStatus() == OrderStatus.RENTAL_RETURN_REQUESTED) {
            for (OrderItem item : order.getItems()) {
                if (item.getProduct() != null) {
                    Product product = item.getProduct();
                    product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                    product.setIsAvailable(true);
                    productRepository.save(product);
                }
            }

            order.setStatus(OrderStatus.RENTAL_RETURNED);
            order.setReturnedAt(Instant.now());
            Order saved = orderRepository.save(order);
            notificationService.create(order.getUser().getId(), "Yêu cầu trả máy đã được duyệt",
                    "Đơn thuê " + order.getOrderCode() + " đã được ghi nhận trả máy, đang chờ kiểm tra tình trạng.",
                    NotificationType.ORDER_UPDATE, order.getId());
            return toDto(saved);
        }

        throw new ApiException(HttpStatus.BAD_REQUEST, "NO_RETURN_REQUEST", "Đơn hàng hiện không có yêu cầu đổi trả nào đang chờ duyệt");
    }

    @Transactional
    public OrderDto rejectReturn(Long orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng"));

        if (order.getStatus() == OrderStatus.RETURN_REQUESTED) {
            order.setStatus(OrderStatus.COMPLETED);
            order.setReturnRejectReason(reason);
            Order saved = orderRepository.save(order);
            notificationService.create(order.getUser().getId(), "Yêu cầu đổi trả bị từ chối",
                    "Đơn " + order.getOrderCode() + " - lý do: " + reason,
                    NotificationType.ORDER_UPDATE, order.getId());
            return toDto(saved);
        }

        if (order.getStatus() == OrderStatus.RENTAL_RETURN_REQUESTED) {
            order.setStatus(OrderStatus.DELIVERED);
            order.setReturnRejectReason(reason);
            Order saved = orderRepository.save(order);
            notificationService.create(order.getUser().getId(), "Yêu cầu trả máy bị từ chối",
                    "Đơn thuê " + order.getOrderCode() + " chưa được duyệt trả máy: " + reason,
                    NotificationType.ORDER_UPDATE, order.getId());
            return toDto(saved);
        }

        throw new ApiException(HttpStatus.BAD_REQUEST, "NO_RETURN_REQUEST", "Đơn hàng hiện không có yêu cầu đổi trả nào đang chờ duyệt");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void attachItems(Order order, List<OrderItem> items) {
        for (OrderItem item : items) {
            item.setOrder(order);
            order.getItems().add(item);
        }
    }

    private void attachAddons(Order order, List<OrderAddon> addons) {
        for (OrderAddon addon : addons) {
            addon.setOrder(order);
            order.getAddons().add(addon);
        }
    }

    private String generateOrderCode(String prefix) {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        String code;
        do {
            int suffix = 1000 + random.nextInt(9000);
            code = prefix + datePart + suffix;
        } while (orderRepository.existsByOrderCode(code));
        return code;
    }

    private OrderDto toDto(Order order) {
        List<OrderItemDto> itemDtos = order.getItems().stream()
                .map(i -> new OrderItemDto(
                        i.getId(),
                        i.getProduct() != null ? i.getProduct().getId() : null,
                        i.getProductName(),
                        i.getProductImageUrl(),
                        i.getUnitPrice(),
                        i.getQuantity(),
                        i.getRentalDays(),
                        i.getRentalSlot(),
                        i.getSubtotal()
                ))
                .collect(Collectors.toList());

        List<OrderAddonDto> addonDtos = order.getAddons().stream()
                .map(a -> new OrderAddonDto(a.getId(), a.getName(), a.getPrice(), a.isIncluded()))
                .collect(Collectors.toList());

        return new OrderDto(
                order.getId(),
                order.getOrderCode(),
                order.getOrderType(),
                order.getStatus(),
                order.getRecipientName(),
                order.getRecipientPhone(),
                order.getShippingAddress(),
                order.getFulfillmentMethod(),
                order.getPickupLocationName(),
                order.getPickupFee(),
                order.getNote(),
                order.getRentalStartDate(),
                order.getRentalEndDate(),
                order.getRentalDays(),
                order.getSubtotalAmount(),
                order.getPromotionCode(),
                order.getDiscountAmount(),
                order.getLoyaltyDiscountAmount(),
                order.getDepositAmount(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getCompletedAt(),
                order.getReturnReason(),
                order.getReturnImageUrls() != null && !order.getReturnImageUrls().isBlank()
                        ? java.util.Arrays.asList(order.getReturnImageUrls().split(","))
                        : java.util.List.of(),
                order.getReturnRequestedAt(),
                order.getReturnRejectReason(),
                order.getReturnedAt(),
                order.getRefundAmount(),
                order.getId() != null && rentalContractRepository.existsByOrder_Id(order.getId()),
                order.getDepositPaidAt(),
                order.getDeliveredAt(),
                order.getDeliveryConditionNote(),
                order.getInspectedAt(),
                order.getInspectionNote(),
                order.getDamageAmount(),
                order.getDisputeReason(),
                itemDtos,
                addonDtos
        );
    }
}