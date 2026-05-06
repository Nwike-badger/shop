package semicolon.africa.waylchub.dto.customOrderDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Style reference image as sent from the frontend.
 *
 * Two acceptance modes:
 *   - Pre-uploaded: client called POST /v1/custom-uploads first, sends back
 *     the returned URL in {@link #url}.
 *   - Inline base64: client sends a data URL (data:image/png;base64,...) in
 *     {@link #dataUrl}. Server uploads it via StorageService at submit time.
 *
 * Exactly one of the two must be present. Inline mode is supported for
 * backward compatibility with the localStorage-only frontend; new code paths
 * should prefer pre-upload.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StyleImageInput {
    /** URL of an already-uploaded image. */
    private String url;

    /** Base64 data URL — server will upload and replace with a permanent URL. */
    private String dataUrl;

    /** Optional original filename; used to produce a sensible storage path. */
    private String name;
}