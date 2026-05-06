package semicolon.africa.waylchub.dto.customOrderDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageUploadResponse {
    /** Permanent URL to use in subsequent API calls. */
    private String url;

    /** Provider-specific id, useful if we ever need to delete (e.g., Cloudinary public_id). */
    private String publicId;
}