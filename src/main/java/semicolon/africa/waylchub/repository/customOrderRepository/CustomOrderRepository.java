package semicolon.africa.waylchub.repository.customOrderRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import semicolon.africa.waylchub.model.customOrder.CustomOrder;
import semicolon.africa.waylchub.model.customOrder.CustomOrderStatus;

import java.util.Optional;

@Repository
public interface CustomOrderRepository extends MongoRepository<CustomOrder, String> {

    Optional<CustomOrder> findByReferenceNumber(String referenceNumber);

    Optional<CustomOrder> findByIdempotencyKey(String idempotencyKey);

    Page<CustomOrder> findByCustomerId(String customerId, Pageable pageable);

    Page<CustomOrder> findByStatus(CustomOrderStatus status, Pageable pageable);

    boolean existsByReferenceNumber(String referenceNumber);
}