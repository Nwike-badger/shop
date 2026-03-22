package semicolon.africa.waylchub.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * Enables Spring Data MongoDB auditing.
 *
 * WITHOUT this: @CreatedDate and @LastModifiedDate annotations on your
 * model fields are silently ignored — createdAt stays null forever.
 * Symptoms:
 *   - "Date unavailable" on the Orders page
 *   - Orders sorted by createdAt DESC return in random order
 *   - Recently paid orders disappear from the top of the list
 *
 * WITH this: Spring Data automatically populates createdAt on insert
 * and updatedAt on every save, with no changes to your model required
 * (as long as Order already has @CreatedDate on its createdAt field).
 */
@Configuration
@EnableMongoAuditing
public class MongoAuditingConfig {
    // No beans needed — the annotation does all the work
}