package semicolon.africa.waylchub.model.customOrder;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Style portion of a custom order.
 *
 * - selectedStyleId/Name come from the admin gallery (CATEGORIES[].sampleStyles).
 * - referenceImageUrls are uploaded by the client (Pinterest screenshots,
 *   Instagram pics, sketches). Stored as URLs from {@link semicolon.africa.waylchub.service.storage.StorageService}.
 * - At least one of (selectedStyleId, referenceImageUrls) must be present;
 *   validation is enforced in CustomOrderService.submit.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StyleSpec {
    private String selectedStyleId;
    private String selectedStyleName;

    @Builder.Default
    private List<String> referenceImageUrls = new ArrayList<>();

    private String styleNotes;
}