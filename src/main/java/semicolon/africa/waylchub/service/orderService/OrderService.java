package semicolon.africa.waylchub.service.orderService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import semicolon.africa.waylchub.dto.orderDto.OrderItemRequest;
import semicolon.africa.waylchub.dto.orderDto.OrderRequest;
import semicolon.africa.waylchub.dto.orderDto.OrderResponse;
import semicolon.africa.waylchub.model.order.Order;
import semicolon.africa.waylchub.model.order.OrderItem;
import semicolon.africa.waylchub.model.product.Product;
import semicolon.africa.waylchub.repository.OrderRepository;
import semicolon.africa.waylchub.repository.productRepository.ProductRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;



@Service
public class OrderService {

    @Autowired private OrderRepository orderRepository;
    @Autowired private ProductRepository productRepository;

    @Transactional
    public Order placeOrder(OrderRequest request, String authenticatedEmail) {
        BigDecimal total = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (OrderItemRequest itemReq : request.getItems()) {

            // MongoDB findById expects String
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found with ID: " + itemReq.getProductId()));

            if (product.getStockQuantity() < itemReq.getQuantity()) {
                throw new RuntimeException("Insufficient stock: " + product.getName());
            }

            // Reduce stock
            product.setStockQuantity(product.getStockQuantity() - itemReq.getQuantity());
            productRepository.save(product);

            // Build OrderItem Entity
            OrderItem item = new OrderItem();
            item.setProductId(product.getId()); // String ID
            item.setProductName(product.getName());
            item.setQuantity(itemReq.getQuantity());
            item.setUnitPrice(product.getPrice());
            item.setSubTotal(product.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity())));

            orderItems.add(item);
            total = total.add(item.getSubTotal());
        }

        Order order = new Order();
        order.setCustomerEmail(authenticatedEmail);
        order.setItems(orderItems);
        order.setTotalAmount(total);
        order.setStatus("SUCCESS");

        return orderRepository.save(order);
    }
}
