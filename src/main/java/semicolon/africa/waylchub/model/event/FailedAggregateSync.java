package semicolon.africa.waylchub.model.event;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "failed_aggregate_syncs")
public class FailedAggregateSync {

    @Id
    private String id;

    private String productId;

    private String variantId;

    private String reason; // Why did it fail? (e.g., "OptimisticLockingFailureException")

    private String errorMessage;

    private boolean resolved; // Flag to mark when the background job fixes it

    private int attemptCount; // How many times the background job has tried to fix it

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}