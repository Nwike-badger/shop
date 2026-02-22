package semicolon.africa.waylchub.service.orderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import semicolon.africa.waylchub.dto.orderDto.OrderItemRequest;
import semicolon.africa.waylchub.dto.orderDto.OrderRequest;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductService productService;
    @Value("${app.tax.vat-rate:0.075}")
    private String vatRate;

    @Transactional
    public Order createOrder(OrderRequest request) {
        log.info("Initiating checkout for customer: {}", request.getCustomerEmail());

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal itemSubTotal = BigDecimal.ZERO;

        for (OrderItemRequest itemReq : request.getItems()) {

            ProductVariant variant = variantRepository.findById(itemReq.getVariantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + itemReq.getVariantId()));

            // ✅ FIX 1: Fetch the parent product to get the REAL name for the receipt
            Product parentProduct = productService.getProductById(variant.getProductId());

            productService.reduceStockAtomic(variant.getId(), itemReq.getQuantity());

            BigDecimal realPrice = variant.getPrice() != null ? variant.getPrice() : BigDecimal.ZERO;
            BigDecimal lineTotal = realPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity()));

            // ✅ FIX: Extract the actual string URL from the ProductImage object
            String primaryImageUrl = null;
            if (variant.getImages() != null && !variant.getImages().isEmpty()) {
                primaryImageUrl = variant.getImages().get(0).getUrl(); // Getting the string URL
            }

            OrderItem orderItem = OrderItem.builder()
                    .productId(variant.getProductId())
                    .variantId(variant.getId())
                    .sku(variant.getSku())
                    .productName(parentProduct.getName()) // ✅ Now shows "Honda Civic" instead of "Product Snapshot"
                    .imageUrl(primaryImageUrl)
                    .variantAttributes(variant.getAttributes())
                    .quantity(itemReq.getQuantity())
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

        Order savedOrder = orderRepository.save(order);
        log.info("Order successfully created. Order Number: {}", savedOrder.getOrderNumber());

        return savedOrder;
    }

    // ... (updateOrderStatus and cancelOrder remain the same) ...
    @Transactional
    public Order updateOrderStatus(String orderId, OrderStatus newStatus, String note) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        order.setOrderStatus(newStatus);
        addStatusHistory(order, newStatus, note);

        return orderRepository.save(order);
    }

    @Transactional
    public Order cancelOrder(String orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (order.getOrderStatus() == OrderStatus.CANCELLED || order.getOrderStatus() == OrderStatus.DELIVERED) {
            throw new IllegalStateException("Cannot cancel an order in state: " + order.getOrderStatus());
        }

        order.setOrderStatus(OrderStatus.CANCELLED);
        addStatusHistory(order, OrderStatus.CANCELLED, "Cancelled: " + reason);

        for (OrderItem item : order.getItems()) {
            productService.addStockAtomic(item.getVariantId(), item.getProductId(), item.getQuantity());
        }

        log.info("Order {} cancelled and stock restored.", order.getOrderNumber());
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
        return new BigDecimal("2500.00").setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateTax(BigDecimal subTotal) {
        // Use the injected value instead of the hardcoded one
        return subTotal.multiply(new BigDecimal(vatRate)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateDiscount(String promoCode, BigDecimal subTotal) {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}