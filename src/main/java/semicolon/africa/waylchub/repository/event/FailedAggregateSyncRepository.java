package semicolon.africa.waylchub.repository.event;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import semicolon.africa.waylchub.model.event.FailedAggregateSync;

import java.util.List;

@Repository
public interface FailedAggregateSyncRepository extends MongoRepository<FailedAggregateSync, String> {

    // Custom query to find all unresolved failures so a cron job can process them
    List<FailedAggregateSync> findByResolvedFalse();
}