package semicolon.africa.waylchub.dto.recommendation;

/**
 * Fire-and-forget tracking payload.
 * All fields optional — the tracking service handles nulls gracefully.
 */
public record TrackEventRequest(
        String productId,
        String variantId,
        String query        // for SEARCH events
) {}