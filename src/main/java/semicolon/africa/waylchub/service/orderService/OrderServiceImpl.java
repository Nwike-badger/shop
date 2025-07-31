package semicolon.africa.waylchub.service.orderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import semicolon.africa.waylchub.dto.orderDto.OrderRequest;
import semicolon.africa.waylchub.dto.orderDto.OrderResponse;
import semicolon.africa.waylchub.dto.orderDto.OrderItemRequest;
import semicolon.africa.waylchub.dto.orderDto.PaymentDetailsRequest;
import semicolon.africa.waylchub.exception.InsufficientStockException;
import semicolon.africa.waylchub.exception.PaymentProcessingException;
import semicolon.africa.waylchub.exception.ProductNotFoundException;
import semicolon.africa.waylchub.mapper.OrderMapper;
import semicolon.africa.waylchub.model.order.Order;
import semicolon.africa.waylchub.model.order.OrderItem;
import semicolon.africa.waylchub.model.order.OrderStatus;
import semicolon.africa.waylchub.model.order.PaymentDetails;
import semicolon.africa.waylchub.model.order.PaymentStatus;
import semicolon.africa.waylchub.model.product.Product;
import semicolon.africa.waylchub.repository.OrderRepository;
import semicolon.africa.waylchub.repository.productRepository.ProductRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID; // For mock transaction ID

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public OrderResponse placeOrder(OrderRequest request, String userId) {
        log.info("Attempting to place order for user: {}", userId);


        PaymentDetails processedPaymentDetails;
        PaymentStatus paymentStatus;

        if ("card".equalsIgnoreCase(request.getPaymentMethod())) {
            // This is your mock payment gateway logic
            PaymentDetailsRequest cardDetails = request.getPaymentDetails();
            if (cardDetails == null) {
                throw new PaymentProcessingException("Card details are required for card payment.");
            }

            // --- MOCK PAYMENT LOGIC START ---
            // In a real scenario, you'd call an external payment gateway API here (Paystack, Flutterwave, Stripe etc.)
            // e.g., paystackService.chargeCard(cardDetails.getCardNumber(), request.getTotalAmount(), userId)
            boolean paymentSuccess = mockProcessPayment(cardDetails, request.getTotalAmount());

            if (paymentSuccess) {
                paymentStatus = PaymentStatus.SUCCESS;
                processedPaymentDetails = PaymentDetails.builder()
                        .cardNumberLastFour(cardDetails.getCardNumber().substring(cardDetails.getCardNumber().length() - 4))
                        .cardType(detectCardType(cardDetails.getCardNumber())) // Basic card type detection
                        .transactionId(UUID.randomUUID().toString()) // Mock transaction ID
                        .gatewayResponseCode("00")
                        .gatewayResponseMessage("Approved")
                        .build();
                log.info("Mock payment successful for order total: {}", request.getTotalAmount());
            } else {
                paymentStatus = PaymentStatus.FAILED;
                processedPaymentDetails = PaymentDetails.builder()
                        .cardNumberLastFour(cardDetails.getCardNumber().substring(cardDetails.getCardNumber().length() - 4))
                        .cardType(detectCardType(cardDetails.getCardNumber()))
                        .transactionId(null) // No transaction ID on failure
                        .gatewayResponseCode("01") // Example error code
                        .gatewayResponseMessage("Declined by mock gateway")
                        .build();
                throw new PaymentProcessingException("Payment failed. Please check your card details or try again.");
            }
            // --- MOCK PAYMENT LOGIC END ---

        } else if ("cash_on_delivery".equalsIgnoreCase(request.getPaymentMethod())) {
            paymentStatus = PaymentStatus.PENDING; // Payment will be collected on delivery
            processedPaymentDetails = null; // No card details for COD
            log.info("Cash on Delivery selected. Order will be marked as PENDING payment.");
        } else {
            throw new IllegalArgumentException("Invalid payment method: " + request.getPaymentMethod());
        }


        // 2. Validate stock and deduct quantities (only if payment was successful or COD)
        // This part runs AFTER payment processing for card payments
        List<OrderItem> processedItems = new ArrayList<>();
        if (paymentStatus == PaymentStatus.SUCCESS || paymentStatus == PaymentStatus.PENDING) {
            for (OrderItemRequest itemRequest : request.getItems()) {
                Product product = productRepository.findById(itemRequest.getProductId())
                        .orElseThrow(() -> new ProductNotFoundException("Product not found with ID: " + itemRequest.getProductId()));

                if (product.getQuantity() < itemRequest.getQuantity()) {
                    // Revert payment if stock fails after payment (complex, usually handled by gateway callbacks)
                    // For now, if payment passed and stock fails, it's an issue.
                    // A real system would have compensation logic or pre-check stock.
                    throw new InsufficientStockException("Insufficient stock for product: " + product.getName() +
                            ". Available: " + product.getQuantity() + ", Requested: " + itemRequest.getQuantity());
                }

                int updatedQuantity = product.getQuantity() - itemRequest.getQuantity();
                if (updatedQuantity <= 0) {
                    productRepository.delete(product);
                    log.info("Product with ID {} has been deleted due to zero stock.", product.getId());
                } else {
                    product.setQuantity(updatedQuantity);
                    product.setUpdatedAt(LocalDateTime.now());
                    productRepository.save(product);
                }


                processedItems.add(OrderItem.builder()
                        .productId(product.getId())
                        .productName(product.getName())
                        .imageUrl(product.getImageUrls() != null && !product.getImageUrls().isEmpty() ? product.getImageUrls().get(0) : null)
                        .quantity(itemRequest.getQuantity())
                        .priceAtPurchase(product.getPrice())
                        .build());
            }
        } else {
            // Payment failed, do not proceed with order creation or stock deduction
            throw new PaymentProcessingException("Order cannot be placed due to payment failure.");
        }


        // 3. Create the Order
        Order order = OrderMapper.toOrder(request, userId);
        order.setItems(processedItems);
        order.setPaymentDetails(processedPaymentDetails); // Set payment details
        order.setPaymentStatus(paymentStatus); // Set payment status
        order.setOrderDate(LocalDateTime.now());
        order.setLastUpdated(LocalDateTime.now());

        // Set order status based on payment status
        if (paymentStatus == PaymentStatus.SUCCESS) {
            order.setOrderStatus(OrderStatus.PROCESSING); // Or 'PENDING' if further human action is needed
        } else if (paymentStatus == PaymentStatus.PENDING) { // For COD
            order.setOrderStatus(OrderStatus.PENDING);
        } else {
            // This case should ideally be prevented by the `if (paymentStatus == PaymentStatus.SUCCESS || paymentStatus == PaymentStatus.PENDING)` check above
            order.setOrderStatus(OrderStatus.CANCELED); // Mark as cancelled if payment failed
            log.error("Order status set to CANCELED due to payment failure.");
        }


        Order savedOrder = orderRepository.save(order);
        log.info("Order placed successfully with ID: {}", savedOrder.getId());

        return OrderMapper.toOrderResponse(savedOrder);
    }

    // --- Helper Methods for MOCK Payment Gateway ---

    /**
     * Mocks a payment gateway processing.
     * Always returns true for success for now.
     * In a real scenario, this would involve calling external APIs.
     *
     * @param cardDetails The card details from the request.
     * @param amount The total amount to charge.
     * @return true if mock payment is successful, false otherwise.
     */
    private boolean mockProcessPayment(PaymentDetailsRequest cardDetails, BigDecimal amount) {
        log.info("Mock processing payment for amount {} with card ending in {}", amount, cardDetails.getCardNumber().substring(cardDetails.getCardNumber().length() - 4));
        // Simulate a delay
        try {
            Thread.sleep(1000); // Simulate network latency
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // For now, always accept. You can add logic here to simulate failures.
        // e.g., if ("4111222233334444".equals(cardDetails.getCardNumber())) return false;
        // Check expiry date
        int currentYear = LocalDateTime.now().getYear();
        int currentMonth = LocalDateTime.now().getMonthValue();
        int expiryYear = Integer.parseInt(cardDetails.getExpiryYear());
        int expiryMonth = Integer.parseInt(cardDetails.getExpiryMonth());

        if (expiryYear < currentYear || (expiryYear == currentYear && expiryMonth < currentMonth)) {
            log.warn("Mock payment failed: Card expired.");
            return false;
        }

        // Basic CVV check (not real validation)
        if (cardDetails.getCvv().length() < 3) {
            log.warn("Mock payment failed: Invalid CVV.");
            return false;
        }

        return true; // Always return true for mock success
    }

    /**
     * Basic card type detection based on starting digits.
     * Not exhaustive, just for mock purposes.
     */
    private String detectCardType(String cardNumber) {
        if (cardNumber == null || cardNumber.isEmpty()) {
            return "UNKNOWN";
        }
        if (cardNumber.startsWith("4")) return "Visa";
        if (cardNumber.startsWith("5")) return "Mastercard";
        if (cardNumber.startsWith("34") || cardNumber.startsWith("37")) return "Amex";
        if (cardNumber.startsWith("6011") || cardNumber.startsWith("65")) return "Discover";
        return "Generic Card";
    }
}
