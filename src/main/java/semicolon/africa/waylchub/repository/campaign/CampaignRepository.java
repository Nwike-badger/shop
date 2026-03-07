package semicolon.africa.waylchub.repository.campaign;

import org.springframework.data.mongodb.repository.MongoRepository;
import semicolon.africa.waylchub.model.campaign.Campaign;

import java.time.Instant;
import java.util.List;

public interface CampaignRepository extends MongoRepository<Campaign, String> {

    List<Campaign> findByActiveFalseAndStartDateLessThanEqualAndEndDateGreaterThan(
            Instant now, Instant nowAgain);

    List<Campaign> findByActiveTrueAndEndDateLessThanEqual(Instant now);

    List<Campaign> findByActiveTrue();
}