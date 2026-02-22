package semicolon.africa.waylchub.service.orderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import semicolon.africa.waylchub.dto.orderDto.OrderItemRequest;
import semicolon.africa.waylchub.dto.orderDto.OrderRequest;
import semicolon.africa.waylchub.event.OrderCancelledEvent;
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

    @Transactional
    public Order createOrder(OrderRequest request) {
        log.info("Initiating checkout for customer: {}", request.getCustomerEmail());

        // Consolidate duplicates from the frontend
        Map<String, Integer> consolidatedItems = request.getItems().stream()
                .collect(Collectors.groupingBy(OrderItemRequest::getVariantId,
                        Collectors.summingInt(OrderItemRequest::getQuantity)));

        Set<String> variantIds = consolidatedItems.keySet();
        List<ProductVariant> variants = variantRepository.findByIdIn(variantIds);
        Map<String, ProductVariant> variantMap = variants.stream()
                .collect(Collectors.toMap(ProductVariant::getId, v -> v));

        Set<String> productIds = variants.stream()
                .map(ProductVariant::getProductId)
                .collect(Collectors.toSet());
        List<Product> products = productService.getProductsByIds(productIds);
        Map<String, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal itemSubTotal = BigDecimal.ZERO;

        for (Map.Entry<String, Integer> entry : consolidatedItems.entrySet()) {
            String variantId = entry.getKey();
            int quantity = entry.getValue();

            ProductVariant variant = variantMap.get(variantId);
            if (variant == null) {
                throw new ResourceNotFoundException("Variant not found: " + variantId);
            }

            Product parentProduct = productMap.get(variant.getProductId());

            // ✅ CONTRACT REQUIREMENT 1: Ensure Product is Active
            if (parentProduct == null || !parentProduct.isActive()) {
                throw new IllegalStateException("Product is no longer available for purchase: " +
                        (parentProduct != null ? parentProduct.getName() : "Unknown"));
            }

            // Deduct stock
            productService.reduceStockAtomic(variant.getId(), quantity);

            BigDecimal realPrice = variant.getPrice() != null ? variant.getPrice() : BigDecimal.ZERO;
            BigDecimal lineTotal = realPrice.multiply(BigDecimal.valueOf(quantity));

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
                    .unitPrice(realPrice)
                    .subTotal(lineTotal)
                    .build();

            orderItems.add(orderItem);
            itemSubTotal = itemSubTotal.add(lineTotal);
        }

        BigDecimal shippingFee = calculateShippingFee(request.getShippingAddress(), itemSubTotal);
        BigDecimal taxAmount = calculateTax(itemSubTotal);
        BigDecimal discountAmount = calculateDiscount(request.getAppliedPromoCode(), itemSubTotal);

        BigDecimal grandTotal = itemSubTotal.add(shippingFee).add(taxAmount).subtract(discountAmount);

        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .customerId(request.getCustomerId())
                .customerEmail(request.getCustomerEmail())
                .items(orderItems)
                .currency("NGN")
                .itemSubTotal(itemSubTotal)
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

        addStatusHistory(order, OrderStatus.PENDING_PAYMENT, "Order placed, awaiting payment validation");

        // ✅ CONTRACT REQUIREMENT 2: Rollback Protection
        try {
            Order savedOrder = orderRepository.save(order);
            log.info("Order successfully created. Order Number: {}", savedOrder.getOrderNumber());
            return savedOrder;
        } catch (Exception e) {
            log.error("CRITICAL: Failed to save order for {}. Rolling back stock deductions.", request.getCustomerEmail(), e);

            // Revert all atomic stock deductions made in the loop above
            for (OrderItem item : orderItems) {
                try {
                    productService.addStockAtomic(item.getVariantId(), item.getProductId(), item.getQuantity());
                } catch (Exception rollbackEx) {
                    log.error("DISASTER: Failed to rollback stock for variant {}", item.getVariantId(), rollbackEx);
                }
            }

            // Re-throw to inform the user the checkout failed
            throw new RuntimeException("Checkout failed due to a system error. Your cart stock has been released.");
        }
    }

    @Transactional
    public Order cancelOrder(String orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        // 🛡️ ENTERPRISE FIX: State Machine validation for Shipped/Delivered
        if (order.getOrderStatus() == OrderStatus.SHIPPED || order.getOrderStatus() == OrderStatus.DELIVERED) {
            throw new IllegalStateException("Cannot cancel an order that has already been shipped or delivered. Please initiate a return.");
        }
        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Order is already cancelled.");
        }

        order.setOrderStatus(OrderStatus.CANCELLED);
        addStatusHistory(order, OrderStatus.CANCELLED, "Cancelled: " + reason);

        Order savedOrder = orderRepository.save(order);

        // 🛡️ ENTERPRISE FIX: Async Event-Driven Stock Restoration
        eventPublisher.publishEvent(new OrderCancelledEvent(savedOrder.getId(), savedOrder.getItems()));
        log.info("Order {} cancelled. Async stock restoration event published.", order.getOrderNumber());

        return savedOrder;
    }

    @Transactional
    public Order updateOrderStatus(String orderId, OrderStatus newStatus, String note) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        order.setOrderStatus(newStatus);
        addStatusHistory(order, newStatus, note);
        return orderRepository.save(order);
    }

    public Order getOrderByNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderNumber));
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
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomPart = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return "ORD-" + datePart + "-" + randomPart;
    }

    private BigDecimal calculateShippingFee(Address address, BigDecimal subTotal) {
        // ✅ FIX 2: Explicitly scale all currency to 2 decimal places to prevent payment gateway crashes
        return new BigDecimal(shippingFee).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateTax(BigDecimal subTotal) {
        return subTotal.multiply(new BigDecimal(vatRate)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateDiscount(String promoCode, BigDecimal subTotal) {
        // 🛡️ ENTERPRISE FIX: Handle null/blank promo codes safely
        if (promoCode == null || promoCode.trim().isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        // TODO: Validate promo code from database and return actual discount amount
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}