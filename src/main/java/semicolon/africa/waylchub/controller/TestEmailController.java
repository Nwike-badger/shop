package semicolon.africa.waylchub.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import semicolon.africa.waylchub.event.OrderPaidEvent;
import semicolon.africa.waylchub.model.order.Order;
import semicolon.africa.waylchub.model.order.OrderItem;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/test-email")
@RequiredArgsConstructor
public class TestEmailController {

    private final ApplicationEventPublisher eventPublisher;

    @GetMapping("/receipt")
    public ResponseEntity<String> testOrderReceiptEmail() {

        // 1. Create a dummy OrderItem
        OrderItem dummyItem = OrderItem.builder()
                .productName("Aba Made Leather Shoes")
                .quantity(2)
                .unitPrice(new BigDecimal("15000.00"))
                .build();

        // 2. Create a dummy Order
        Order dummyOrder = Order.builder()
                .orderNumber("ORD-TEST-9999")
                // 👇 CHANGE THIS TO YOUR ACTUAL PERSONAL EMAIL ADDRESS 👇
                .customerEmail("CHRISCHIDI29@GMAIL.COM")
                .items(List.of(dummyItem))
                .grandTotal(new BigDecimal("30000.00"))
                .currency("NGN")
                .build();

        // 3. Fire the event!
        eventPublisher.publishEvent(new OrderPaidEvent(dummyOrder, "TXN-TEST-12345"));

        return ResponseEntity.ok("Test email event fired! Check your Spring Boot console and your inbox.");
    }
}