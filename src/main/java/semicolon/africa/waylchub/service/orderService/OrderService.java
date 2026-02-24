package semicolon.africa.waylchub.service.orderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import semicolon.africa.waylchub.dto.orderDto.OrderItemRequest;
import semicolon.africa.waylchub.dto.orderDto.OrderRequest;
import semicolon.africa.waylchub.event.OrderCancelledEvent;
import semicolon.africa.waylchub.exception.InsufficientStockException;
import semicolon.africa.waylchub.exception.ResourceNotFoundException;
import semicolon.africa.waylchub.model.order.*;
import semicolon.africa.waylchub.model.product.Product;
import semicolon.africa.waylchub.model.product.ProductVariant;
import semicolon.africa.waylchub.repository.orderRepository.OrderRepository;
import semicolon.africa.waylchub.repository.productRepository.ProductVariantRepository;
import semicolon.africa.waylchub.service.productService.ProductService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ✅ PRODUCTION-READY ORDER SERVICE
 *
 * CRITICAL FIXES APPLIED:
 * 1. Removed manual rollback (Spring @Transactional handles it)
 * 2. Added stock validation BEFORE reduction
 * 3. Proper exception handling
 * 4. Defensive null checks
 * 5. Consistent BigDecimal scaling
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductService productService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.tax.vat-rate:0.075}")
    private String vatRate;

    @Value("${app.shippingFee:0}")
    private String shippingFee;

    /**
     * ✅ CRITICAL FIX #1: Removed Manual Rollback
     *
     * WHY THE OLD CODE WAS BROKEN:
     * The original code had this in the catch block:
     *
     * ```java
     * catch (Exception e) {
     *     for (OrderItem item : orderItems) {
     *         productService.addStockAtomic(item.getVariantId(), ...);  // ❌ WRONG!
     *     }
     * }
     * ```
     *
     * PROBLEM:
     * - This method is @Transactional
     * - When exception is thrown, Spring AUTOMATICALLY rolls back ALL database changes
     * - This includes the reduceStockAtomic() calls made earlier
     * - The manual addStockAtomic() calls will ADD extra stock on top of the rollback
     * - Result: Stock count goes UP instead of staying the same!
     *
     * SCENARIO:
     * 1. Variant has 100 stock
     * 2. User checks out 5 items
     * 3. reduceStockAtomic() reduces to 95
     * 4. Order save fails
     * 5. Spring rolls back → stock back to 100 (automatic)
     * 6. Manual restore adds 5 more → stock becomes 105!!! (BUG)
     *
     * FIX:
     * - Remove the manual rollback
     * - Trust Spring's @Transactional to handle rollback
     * - MongoDB transactions will restore stock automatically
     */
    @Transactional
    // ✅ Keep this configuration. It is robust.
    @Retryable(
            value = {
                    OptimisticLockingFailureException.class,
                    DataIntegrityViolationException.class
            },
            maxAttempts = 10, // 10 is plenty if the delay is random
            backoff = @Backoff(delay = 100, maxDelay = 2000, random = true)
    )
    public Order createOrder(OrderRequest request) {
        log.info("Initiating checkout for customer: {}", request.getCustomerEmail());

        // ... (Consolidation logic remains the same) ...
        Map<String, Integer> consolidatedItems = request.getItems().stream()
                .collect(Collectors.groupingBy(
                        OrderItemRequest::getVariantId,
                        Collectors.summingInt(OrderItemRequest::getQuantity)
                ));

        Set<String> variantIds = consolidatedItems.keySet();
        List<ProductVariant> variants = variantRepository.findByIdIn(variantIds);

        if (variants.size() != variantIds.size()) {
            throw new ResourceNotFoundException("One or more variants not found in your cart");
        }

        Map<String, ProductVariant> variantMap = variants.stream()
                .collect(Collectors.toMap(ProductVariant::getId, v -> v));

        Set<String> productIds = variants.stream()
                .map(ProductVariant::getProductId)
                .collect(Collectors.toSet());
        List<Product> products = productService.getProductsByIds(productIds);
        Map<String, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        // Validate BEFORE reducing
        validateStockAvailability(consolidatedItems, variantMap, productMap);

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal itemSubTotal = BigDecimal.ZERO;

        for (Map.Entry<String, Integer> entry : consolidatedItems.entrySet()) {
            String variantId = entry.getKey();
            int quantity = entry.getValue();

            ProductVariant variant = variantMap.get(variantId);
            Product parentProduct = productMap.get(variant.getProductId());

            // ⚠️ CRITICAL FIX HERE ⚠️
            try {
                productService.reduceStockAtomic(variant.getId(), quantity);
            } catch (DataIntegrityViolationException | OptimisticLockingFailureException e) {
                // 🛑 STOP! Do not wrap this in InsufficientStockException.
                // Throw it raw so @Retryable can catch it and retry the transaction.
                throw e;
            } catch (RuntimeException e) {
                // Only catch OTHER runtime errors (like actual business logic failures)
                log.error("Unexpected stock reduction failure for variant {}", variantId, e);
                throw new InsufficientStockException(
                        "Stock changed during checkout for: " + parentProduct.getName()
                );
            }

            // ... (Rest of loop remains the same) ...
            BigDecimal unitPrice = variant.getPrice() != null ?
                    variant.getPrice() : BigDecimal.ZERO;
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity))
                    .setScale(2, RoundingMode.HALF_UP);

            String primaryImageUrl = (variant.getImages() != null && !variant.getImages().isEmpty())
                    ? variant.getImages().get(0).getUrl() : null;

            OrderItem orderItem = OrderItem.builder()
                    .productId(variant.getProductId())
                    .variantId(variant.getId())
                    .sku(variant.getSku())
                    .productName(parentProduct.getName())
                    .imageUrl(primaryImageUrl)
                    .variantAttributes(variant.getAttributes())
                    .quantity(quantity)
                    .unitPrice(unitPrice)
                    .subTotal(lineTotal)
                    .build();

            orderItems.add(orderItem);
            itemSubTotal = itemSubTotal.add(lineTotal);
        }

        // ... (Total calculation and saving logic remains the same) ...
        BigDecimal shippingFee = calculateShippingFee(request.getShippingAddress(), itemSubTotal);
        BigDecimal taxAmount = calculateTax(itemSubTotal);
        BigDecimal discountAmount = calculateDiscount(request.getAppliedPromoCode(), itemSubTotal);

        BigDecimal grandTotal = itemSubTotal
                .add(shippingFee)
                .add(taxAmount)
                .subtract(discountAmount)
                .setScale(2, RoundingMode.HALF_UP);

        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .customerId(request.getCustomerId())
                .customerEmail(request.getCustomerEmail())
                .items(orderItems)
                .currency("NGN")
                .itemSubTotal(itemSubTotal.setScale(2, RoundingMode.HALF_UP))
                .shippingFee(shippingFee)
                .taxAmount(taxAmount)
                .discountAmount(discountAmount)
                .grandTotal(grandTotal)
                .orderStatus(OrderStatus.PENDING_PAYMENT)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentMethod(request.getPaymentMethod())
                .shippingAddress(request.getShippingAddress())
                .billingAddress(request.getBillingAddress())
                .orderNotes(request.getOrderNotes())
                .appliedPromoCode(request.getAppliedPromoCode())
                .build();

        addStatusHistory(order, OrderStatus.PENDING_PAYMENT,
                "Order placed, awaiting payment validation");

        Order savedOrder = orderRepository.save(order);

        log.info("Order successfully created. Order Number: {}", savedOrder.getOrderNumber());
        return savedOrder;
    }

    /**
     * ✅ NEW METHOD: Validate Stock Before Reducing
     *
     * WHY THIS IS CRITICAL:
     * - Checks stock availability BEFORE making any changes
     * - Prevents unnecessary atomic operations that would need rollback
     * - Gives user clear error messages about which items are unavailable
     * - Respects manageStock flag (digital products have infinite stock)
     */
    private void validateStockAvailability(
            Map<String, Integer> consolidatedItems,
            Map<String, ProductVariant> variantMap,
            Map<String, Product> productMap) {

        List<String> unavailableProducts = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : consolidatedItems.entrySet()) {
            String variantId = entry.getKey();
            int requestedQty = entry.getValue();

            ProductVariant variant = variantMap.get(variantId);
            if (variant == null) {
                unavailableProducts.add("Unknown product");
                continue;
            }

            Product product = productMap.get(variant.getProductId());

            // Check if product is active
            if (product == null || !product.isActive()) {
                unavailableProducts.add(product != null ? product.getName() : "Unknown");
                continue;
            }

            // Check if variant is active
            if (!variant.isActive()) {
                unavailableProducts.add(product.getName() + " (variant unavailable)");
                continue;
            }

            // Check stock (only for physical products that manage stock)
            if (variant.isManageStock()) {
                Integer currentStock = variant.getStockQuantity();
                if (currentStock == null || currentStock < requestedQty) {
                    unavailableProducts.add(product.getName() +
                            " (only " + (currentStock != null ? currentStock : 0) + " left)");
                }
            }
        }

        if (!unavailableProducts.isEmpty()) {
            String message = "The following items are unavailable: " +
                    String.join(", ", unavailableProducts);
            throw new InsufficientStockException(message);
        }
    }

    @Transactional
    public Order cancelOrder(String orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        // State machine validation
        if (order.getOrderStatus() == OrderStatus.SHIPPED ||
                order.getOrderStatus() == OrderStatus.DELIVERED) {
            throw new IllegalStateException(
                    "Cannot cancel an order that has been shipped or delivered. " +
                            "Please initiate a return instead."
            );
        }

        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Order is already cancelled.");
        }

        // Update status
        order.setOrderStatus(OrderStatus.CANCELLED);
        addStatusHistory(order, OrderStatus.CANCELLED, "Cancelled: " + reason);

        Order savedOrder = orderRepository.save(order);

        // ✅ CORRECT: Async event-driven stock restoration
        // This is the RIGHT way to restore stock - not manual loops
        eventPublisher.publishEvent(
                new OrderCancelledEvent(savedOrder.getId(), savedOrder.getItems())
        );

        log.info("Order {} cancelled. Stock restoration event published.",
                order.getOrderNumber());

        return savedOrder;
    }

    @Transactional
    public Order updateOrderStatus(String orderId, OrderStatus newStatus, String note) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        // ✅ State validation (prevent invalid transitions)
        validateStatusTransition(order.getOrderStatus(), newStatus);

        order.setOrderStatus(newStatus);
        addStatusHistory(order, newStatus, note);

        return orderRepository.save(order);
    }

    /**
     * ✅ NEW METHOD: State Machine Validation
     * Prevents invalid order status transitions
     */
    private void validateStatusTransition(OrderStatus current, OrderStatus next) {
        // Define valid transitions
        Map<OrderStatus, Set<OrderStatus>> validTransitions = Map.of(
                OrderStatus.PENDING_PAYMENT, Set.of(
                        OrderStatus.PROCESSING, OrderStatus.CANCELLED
                ),
                OrderStatus.PROCESSING, Set.of(
                        OrderStatus.CONFIRMED, OrderStatus.CANCELLED
                ),
                OrderStatus.CONFIRMED, Set.of(
                        OrderStatus.SHIPPED, OrderStatus.CANCELLED
                ),
                OrderStatus.SHIPPED, Set.of(
                        OrderStatus.DELIVERED, OrderStatus.RETURNED
                ),
                OrderStatus.DELIVERED, Set.of(
                        OrderStatus.RETURNED
                )
        );

        Set<OrderStatus> allowed = validTransitions.getOrDefault(current, Set.of());
        if (!allowed.contains(next)) {
            throw new IllegalStateException(
                    "Cannot transition from " + current + " to " + next
            );
        }
    }

    public Order getOrderByNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found: " + orderNumber
                ));
    }

    public Order getOrderById(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found: " + orderId
                ));
    }

    public Page<Order> getCustomerOrders(String customerId, Pageable pageable) {
        return orderRepository.findByCustomerId(customerId, pageable);
    }

    private void addStatusHistory(Order order, OrderStatus status, String note) {
        if (order.getStatusHistory() == null) {
            order.setStatusHistory(new ArrayList<>());
        }

        order.getStatusHistory().add(OrderStatusHistory.builder()
                .status(status)
                .note(note)
                .timestamp(LocalDateTime.now())
                .build());
    }

    private String generateOrderNumber() {
        String datePart = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomPart = UUID.randomUUID()
                .toString()
                .substring(0, 4)
                .toUpperCase();
        return "ORD-" + datePart + "-" + randomPart;
    }

    private BigDecimal calculateShippingFee(Address address, BigDecimal subTotal) {
        // TODO: Implement dynamic shipping based on:
        // - Address location (Lagos, Abuja, etc.)
        // - Order weight
        // - Distance from warehouse
        // - Delivery speed (standard, express)

        return new BigDecimal(shippingFee).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateTax(BigDecimal subTotal) {
        return subTotal
                .multiply(new BigDecimal(vatRate))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateDiscount(String promoCode, BigDecimal subTotal) {
        if (promoCode == null || promoCode.trim().isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        // TODO: Implement PromoCodeService
        // - Validate code exists and is active
        // - Check expiry date
        // - Check usage limits
        // - Calculate discount (percentage or fixed amount)
        // - Mark code as used

        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}